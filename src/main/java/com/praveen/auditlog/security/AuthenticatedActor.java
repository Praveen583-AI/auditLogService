package com.praveen.auditlog.security;

import com.praveen.auditlog.application.AuditRequestContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Set;
import java.util.stream.Collectors;

public record AuthenticatedActor(
        String tenantId,
        String producerId,
        String actorId,
        String actorType,
        String identitySource,
        Set<String> authorities
) {
    public AuthenticatedActor {
        tenantId = required(tenantId, "tenantId");
        producerId = required(producerId, "producerId");
        actorId = required(actorId, "actorId");
        actorType = required(actorType, "actorType");
        identitySource = required(identitySource, "identitySource");
        authorities = Set.copyOf(authorities);
    }

    public static AuthenticatedActor from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Authenticated JWT principal is required");
        }

        String tenantId = required(jwt.getClaimAsString("tenant_id"), "tenant_id");
        String producerId = firstPresent(
                jwt.getClaimAsString("client_id"),
                jwt.getClaimAsString("azp"),
                authentication.getName()
        );
        String actorType = defaulted(jwt.getClaimAsString("actor_type"), "USER");
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());

        return new AuthenticatedActor(
                tenantId,
                producerId,
                required(jwt.getSubject(), "sub"),
                actorType,
                "VERIFIED_JWT",
                authorities
        );
    }

    public AuditRequestContext toRequestContext() {
        return new AuditRequestContext(
                tenantId, producerId, actorId, actorType, identitySource
        );
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalStateException("A trusted producer identity is required");
    }

    private static String defaulted(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String required(String value, String claim) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required trusted identity claim is missing: " + claim);
        }
        return value;
    }
}
