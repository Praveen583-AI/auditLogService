
package com.praveen.auditlog.retention;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.auditlog.integrity.CanonicalEventSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Base64;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "audit.archive.enabled", havingValue = "true")
public class RetentionConfiguration {
    @Bean
    FileArchiveStore fileArchiveStore(
            @Value("${audit.archive.directory}") String directory,
            ObjectMapper objectMapper
    ) {
        return new FileArchiveStore(Path.of(directory), objectMapper);
    }

    @Bean
    RetentionService.ManifestSigner archiveManifestSigner(
            @Value("${audit.archive.signing-key-id}") String keyId,
            @Value("${audit.archive.signing-key-base64}") String encodedKey
    ) {
        return new HmacSha256ManifestSigner(
                keyId, Base64.getDecoder().decode(encodedKey)
        );
    }

    @Bean
    RetentionService retentionService(
            JdbcRetentionRepository repository,
            FileArchiveStore store,
            RetentionService.ManifestSigner signer,
            CanonicalEventSerializer serializer,
            Clock clock
    ) {
        return new RetentionService(repository, store, signer, serializer, clock);
    }
}

