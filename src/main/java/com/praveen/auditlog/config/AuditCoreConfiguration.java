package com.praveen.auditlog.config;

import com.praveen.auditlog.integrity.AuditEventCanonicalizer;
import com.praveen.auditlog.integrity.CanonicalEventSerializer;
import com.praveen.auditlog.integrity.CanonicalJsonAuditEventCanonicalizerV1;
import com.praveen.auditlog.integrity.HashService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

@Configuration
public class AuditCoreConfiguration {

    @Bean
    AuditEventCanonicalizer auditEventCanonicalizer() {
        return new CanonicalJsonAuditEventCanonicalizerV1();
    }

    @Bean
    CanonicalEventSerializer canonicalEventSerializer(
            AuditEventCanonicalizer canonicalizer
    ) {
        return new CanonicalEventSerializer(canonicalizer);
    }

    @Bean
    HashService hashService(CanonicalEventSerializer serializer) {
        return new HashService(serializer);
    }

    @Bean
    Clock auditClock() {
        return Clock.systemUTC();
    }

    @Bean
    Supplier<UUID> auditEventIdSupplier() {
        return UUID::randomUUID;
    }
}
