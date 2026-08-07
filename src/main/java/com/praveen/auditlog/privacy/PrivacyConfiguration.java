package com.praveen.auditlog.privacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.auditlog.integrity.CanonicalEventSerializer;
import com.praveen.auditlog.security.AuthorizationPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

@Configuration
@ConditionalOnProperty(name="audit.privacy.enabled",havingValue="true")
public class PrivacyConfiguration {
    @Bean RedactionService redactionService(JdbcTemplate jdbc,ObjectMapper json,CanonicalEventSerializer canonical,
            AuthorizationPolicy authorization,@Value("${audit.privacy.commitment-key-id}") String keyId,
            @Value("${audit.privacy.commitment-key-base64}") String key) {
        return new RedactionService(jdbc,json,canonical,authorization,keyId,Base64.getDecoder().decode(key),Clock.systemUTC());
    }
    @Bean ExportService exportService(JdbcTemplate jdbc,ObjectMapper json,CanonicalEventSerializer canonical,
            RedactionService redactions,AuthorizationPolicy authorization,
            @Value("${audit.export.signing-private-key-base64}") String privateKey,
            @Value("${audit.export.signing-public-key-base64}") String publicKey,
            @Value("${audit.export.signing-key-id}") String keyId,
            @Value("${audit.export.directory:./build/audit-exports}") String directory,
            @Value("${audit.export.download-ttl:PT15M}") Duration ttl) throws Exception {
        KeyFactory keys=KeyFactory.getInstance("Ed25519");
        PrivateKey signing=keys.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey)));
        PublicKey verification=keys.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
        return new ExportService(jdbc,json,canonical,redactions,authorization,signing,verification,keyId,Path.of(directory),ttl,Clock.systemUTC());
    }
}
