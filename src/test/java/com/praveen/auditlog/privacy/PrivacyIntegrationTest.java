package com.praveen.auditlog.privacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.praveen.auditlog.application.AuditEventSpecification;
import com.praveen.auditlog.application.AuditQueryService;
import com.praveen.auditlog.application.AuditRequestContextProvider;
import com.praveen.auditlog.security.ActorContext;
import com.praveen.auditlog.security.AuthorizationPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class PrivacyIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    private static final KeyPair SIGNING=keyPair();
    private static final Path EXPORTS=Path.of(System.getProperty("java.io.tmpdir"),"audit-export-tests-"+UUID.randomUUID());
    private static final UUID EVENT_ID=UUID.fromString("00000000-0000-0000-0000-000000000101");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        p.add("audit.privacy.enabled",()->"true");
        p.add("audit.privacy.commitment-key-id",()->"privacy-test-1");
        p.add("audit.privacy.commitment-key-base64",()->Base64.getEncoder().encodeToString(new byte[32]));
        p.add("audit.export.signing-key-id",()->"export-test-1");
        p.add("audit.export.signing-private-key-base64",()->Base64.getEncoder().encodeToString(SIGNING.getPrivate().getEncoded()));
        p.add("audit.export.signing-public-key-base64",()->Base64.getEncoder().encodeToString(SIGNING.getPublic().getEncoded()));
        p.add("audit.export.directory",EXPORTS::toString); p.add("audit.export.download-ttl",()->"PT5M");
    }
    @Autowired JdbcTemplate jdbc; @Autowired RedactionService redactions; @Autowired ExportService exports;
    @Autowired AuditQueryService queries; @Autowired ObjectMapper json;
    @MockitoBean AuditRequestContextProvider contextProvider;
    @MockitoBean AuthorizationPolicy authorizationPolicy;

    @BeforeEach void setUp(){
        jdbc.execute("TRUNCATE export_access_action,export_job,redaction_record,idempotency_record,audit_event,chain_head CASCADE");
        byte[] zero=new byte[32]; Instant at=Instant.parse("2026-08-07T18:00:00Z");
        jdbc.update("INSERT INTO chain_head(chain_id,tenant_id,latest_sequence,latest_hash,version,updated_at) VALUES(?,?,?,?,?,?)",
                "tenant:tenant-1","tenant-1",1,zero,1,Timestamp.from(at));
        jdbc.update("INSERT INTO audit_event(event_id,chain_id,tenant_id,sequence_number,event_type,event_schema_version,producer_id,actor_id,actor_type,actor_identity_source,resource_type,resource_id,occurred_at,recorded_at,payload,previous_hash,content_hash,hash_algorithm,canonicalization_version) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?)",
                EVENT_ID,"tenant:tenant-1","tenant-1",1,"ACCOUNT_VIEWED",1,"producer-1","actor-1","USER","JWT","ACCOUNT","account-1",Timestamp.from(at),Timestamp.from(at),"{\"ssn\":\"123-45-6789\",\"public\":\"ok\"}",zero,zero,"SHA-256",1);
        redactions.redact(admin(),EVENT_ID,"/ssn","privacy-policy-1","approved request");
    }

    @Test void normalReadUsesOverlayWhileOriginalAndCommitmentRemainVerifiable(){
        AuditEventSpecification filter=new AuditEventSpecification(null,null,"ACCOUNT","account-1",null,null,null);
        ObjectNode visible=(ObjectNode)queries.search("tenant-1",filter,10,null).items().get(0).payload();
        assertThat(visible.get("ssn").asText()).isEqualTo("[REDACTED]"); assertThat(visible.get("public").asText()).isEqualTo("ok");
        assertThat(jdbc.queryForObject("SELECT payload->>'ssn' FROM audit_event WHERE event_id=?",String.class,EVENT_ID)).isEqualTo("123-45-6789");
        assertThat(redactions.verifyCommitments(compliance(),EVENT_ID)).isTrue();
    }

    @Test void modifiedExportFailsOfflineVerification() throws Exception {
        ExportService.ExportReceipt receipt=exports.create(compliance(),ExportService.SelectorType.RESOURCE_ID,"account-1");
        assertThat(exports.download(compliance(),receipt.exportId(),receipt.downloadToken())).isNotEmpty();
        assertThat(exports.verifyOffline(receipt.artifact())).isTrue();
        ObjectNode bundle=(ObjectNode)json.readTree(Files.readAllBytes(receipt.artifact()));
        ((ObjectNode)bundle.withArray("records").get(0).get("payload")).put("public","tampered");
        Files.write(receipt.artifact(),json.writeValueAsBytes(bundle));
        assertThat(exports.verifyOffline(receipt.artifact())).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM export_access_action WHERE export_id=?",Long.class,receipt.exportId())).isEqualTo(3L);
    }

    private static ActorContext admin(){return actor(ActorContext.Role.AUDIT_ADMIN);}
    private static ActorContext compliance(){return actor(ActorContext.Role.COMPLIANCE_OFFICER);}
    private static ActorContext actor(ActorContext.Role role){return new ActorContext("tenant-1","staff","reviewer-1","USER","JWT",Set.of(role),Set.of("tenant-1"),Set.of());}
    private static KeyPair keyPair(){try{KeyPairGenerator g=KeyPairGenerator.getInstance("Ed25519");return g.generateKeyPair();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
}
