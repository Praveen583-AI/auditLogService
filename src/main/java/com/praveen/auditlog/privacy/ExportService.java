package com.praveen.auditlog.privacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.praveen.auditlog.integrity.CanonicalEventSerializer;
import com.praveen.auditlog.security.ActorContext;
import com.praveen.auditlog.security.AuthorizationPolicy;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public final class ExportService {
    private static final String CHECKSUM = "SHA-256";
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    private final CanonicalEventSerializer canonical; private final RedactionService redactions;
    private final AuthorizationPolicy authorization; private final PrivateKey privateKey;
    private final PublicKey publicKey; private final String keyId; private final Path root;
    private final Duration ttl; private final Clock clock; private final SecureRandom random = new SecureRandom();

    public ExportService(JdbcTemplate jdbc, ObjectMapper json, CanonicalEventSerializer canonical,
                         RedactionService redactions, AuthorizationPolicy authorization,
                         PrivateKey privateKey, PublicKey publicKey, String keyId,
                         Path root, Duration ttl, Clock clock) {
        this.jdbc=jdbc; this.json=json; this.canonical=canonical; this.redactions=redactions;
        this.authorization=authorization; this.privateKey=privateKey; this.publicKey=publicKey;
        this.keyId=keyId; this.root=root; this.ttl=ttl; this.clock=clock;
    }

    public ExportReceipt create(ActorContext actor, SelectorType selector, String selectorValue) {
        authorization.requirePrivilegedJob(actor, AuthorizationPolicy.PrivilegedOperation.EXPORT, actor.tenantId());
        if (selectorValue == null || selectorValue.isBlank()) throw new IllegalArgumentException("selectorValue is required");
        UUID id=UUID.randomUUID(); Instant snapshot=now(); Instant expires=snapshot.plus(ttl);
        jdbc.update("INSERT INTO export_job(export_id,tenant_id,selector_type,selector_value,requested_by,status,requested_at) VALUES (?,?,?,?,?,'REQUESTED',?)",
                id,actor.tenantId(),selector.name(),selectorValue,actor.actorId(),Timestamp.from(snapshot));
        action(id,actor,"REQUESTED","ACCEPTED");
        try {
            Files.createDirectories(root);
            List<JsonNode> records=records(actor.tenantId(),selector,selectorValue);
            List<JsonNode> proofs=proofs(actor.tenantId(),records);
            List<JsonNode> archives=jsonRows("SELECT row_to_json(m)::text FROM archive_manifest m WHERE tenant_id=? ORDER BY chain_id,start_sequence",actor.tenantId());
            List<ExportManifest.ChainBoundary> boundaries=jdbc.query("SELECT chain_id,latest_sequence,latest_hash FROM chain_head WHERE tenant_id=? ORDER BY chain_id",
                    (r,n)->new ExportManifest.ChainBoundary(r.getString(1),r.getLong(2),r.getBytes(3)),actor.tenantId());
            byte[] rc=hash(records), pc=hash(proofs), ac=hash(archives);
            ExportManifest unsigned=new ExportManifest(id,1,actor.tenantId(),selector.name(),selectorValue,snapshot,expires,
                    records.size(),boundaries,rc,pc,ac,CHECKSUM,"Ed25519",keyId,1,new byte[0]);
            byte[] signature=sign(manifestBytes(unsigned));
            ExportManifest manifest=new ExportManifest(id,1,actor.tenantId(),selector.name(),selectorValue,snapshot,expires,
                    records.size(),boundaries,rc,pc,ac,CHECKSUM,"Ed25519",keyId,1,signature);
            ObjectNode bundle=json.createObjectNode(); bundle.put("format","audit-export-v1");
            bundle.set("manifest",json.valueToTree(manifest)); bundle.set("records",json.valueToTree(records));
            bundle.set("redactionProofs",json.valueToTree(proofs)); bundle.set("archiveManifests",json.valueToTree(archives));
            bundle.put("verificationInstructions","Recompute each section SHA-256 checksum, then verify the Ed25519 manifest signature with the identified public key.");
            Path file=root.resolve(id+".json"); Files.write(file,canonical.serializeJson(bundle));
            byte[] token=new byte[32]; random.nextBytes(token); byte[] tokenHash=digest(token);
            jdbc.update("UPDATE export_job SET status='COMPLETED',completed_at=?,expires_at=?,artifact_location=?,download_token_hash=? WHERE export_id=?",
                    Timestamp.from(now()),Timestamp.from(expires),file.toString(),tokenHash,id);
            action(id,actor,"COMPLETED","SUCCESS");
            return new ExportReceipt(id,Base64.getUrlEncoder().withoutPadding().encodeToString(token),expires,file);
        } catch (Exception error) {
            jdbc.update("UPDATE export_job SET status='FAILED',failure_code='EXPORT_BUILD_FAILED' WHERE export_id=?",id);
            action(id,actor,"FAILED","ERROR");
            throw new IllegalStateException("Export could not be created",error);
        }
    }

    public byte[] download(ActorContext actor, UUID id, String token) {
        Job job=jdbc.queryForObject("SELECT tenant_id,status,expires_at,artifact_location,download_token_hash FROM export_job WHERE export_id=?",
                (r,n)->new Job(r.getString(1),r.getString(2),r.getTimestamp(3).toInstant(),Path.of(r.getString(4)),r.getBytes(5)),id);
        authorization.requirePrivilegedJob(actor,AuthorizationPolicy.PrivilegedOperation.EXPORT,job.tenant());
        try {
            byte[] supplied=Base64.getUrlDecoder().decode(token);
            if (!"COMPLETED".equals(job.status()) || !job.expires().isAfter(now())
                    || !MessageDigest.isEqual(job.tokenHash(),digest(supplied))) throw new SecurityException("Export download denied");
            byte[] bytes=Files.readAllBytes(job.path()); action(id,actor,"DOWNLOADED","SUCCESS"); return bytes;
        } catch (Exception denied) { action(id,actor,"DOWNLOAD_DENIED","DENIED"); throw new SecurityException("Export download denied"); }
    }

    public boolean verifyOffline(Path bundleFile) {
        try {
            JsonNode bundle=json.readTree(Files.readAllBytes(bundleFile));
            ExportManifest m=json.treeToValue(bundle.get("manifest"),ExportManifest.class);
            if (!MessageDigest.isEqual(m.recordsChecksum(),hash(bundle.get("records")))
                    || !MessageDigest.isEqual(m.redactionProofsChecksum(),hash(bundle.get("redactionProofs")))
                    || !MessageDigest.isEqual(m.archiveManifestsChecksum(),hash(bundle.get("archiveManifests")))) return false;
            Signature verifier=Signature.getInstance("Ed25519"); verifier.initVerify(publicKey);
            verifier.update(manifestBytes(m)); return verifier.verify(m.signature());
        } catch (Exception invalid) { return false; }
    }

    private List<JsonNode> records(String tenant, SelectorType selector, String value) {
        String column=selector==SelectorType.ACTOR_ID?"actor_id":"resource_id";
        return jdbc.query("SELECT row_to_json(e)::text FROM audit_event e WHERE tenant_id=? AND "+column+"=? ORDER BY chain_id,sequence_number",
                (r,n)->{ try { ObjectNode event=(ObjectNode)json.readTree(r.getString(1)); UUID id=UUID.fromString(event.get("event_id").asText());
                    event.set("payload",redactions.apply(tenant,id,event.get("payload"))); return event; }
                catch(Exception x){throw new IllegalStateException("Stored event is invalid",x);} },tenant,value);
    }
    private List<JsonNode> proofs(String tenant,List<JsonNode> records) {
        return records.stream().flatMap(e->redactions.records(tenant,UUID.fromString(e.get("event_id").asText())).stream())
                .map(record -> (JsonNode) json.valueToTree(record)).toList();
    }
    private List<JsonNode> jsonRows(String sql,Object...args){return jdbc.query(sql,(r,n)->{try{return json.readTree(r.getString(1));}catch(Exception x){throw new IllegalStateException(x);}},args);}
    private byte[] hash(Object value){return digest(canonical.serializeJson(json.valueToTree(value)));}
    private byte[] digest(byte[] value){try{return MessageDigest.getInstance(CHECKSUM).digest(value);}catch(Exception x){throw new IllegalStateException(x);}}
    private byte[] sign(byte[] value)throws Exception{Signature s=Signature.getInstance("Ed25519");s.initSign(privateKey);s.update(value);return s.sign();}
    private byte[] manifestBytes(ExportManifest m){ObjectNode node=json.valueToTree(m);node.remove("signature");return canonical.serializeJson(node);}
    private void action(UUID id,ActorContext actor,String type,String outcome){jdbc.update("INSERT INTO export_access_action(action_id,export_id,tenant_id,actor_id,action_type,outcome,recorded_at) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID(),id,actor.tenantId(),actor.actorId(),type,outcome,Timestamp.from(now()));}
    private Instant now(){return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);}
    public enum SelectorType { ACTOR_ID, RESOURCE_ID }
    public record ExportReceipt(UUID exportId,String downloadToken,Instant expiresAt,Path artifact) {}
    private record Job(String tenant,String status,Instant expires,Path path,byte[] tokenHash) {}
}
