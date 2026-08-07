package com.praveen.auditlog.security;

import com.praveen.auditlog.application.AuditRequestContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public record AuthenticatedActor(
        String tenantId,
        String producerId,
        String actorId,
        String actorType,
        String identitySource,
        Set<String> authorities,
        Set<String> permittedTenantIds,
        Set<String> permittedResourceTypes
) {
    public AuthenticatedActor {
        tenantId = required(tenantId, "tenantId");
        producerId = required(producerId, "producerId");
        actorId = required(actorId, "actorId");
        actorType = required(actorType, "actorType");
        identitySource = required(identitySource, "identitySource");
        authorities = Set.copyOf(authorities);
        permittedTenantIds = Set.copyOf(permittedTenantIds);
        permittedResourceTypes = Set.copyOf(permittedResourceTypes);
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
        Set<String> tenants = new HashSet<>(claimStrings(jwt, "tenant_ids"));
        tenants.add(tenantId);

        return new AuthenticatedActor(
                tenantId,
                producerId,
                required(jwt.getSubject(), "sub"),
                actorType,
                "VERIFIED_JWT",
                authorities,
                tenants,
                claimStrings(jwt, "resource_types")
        );
    }

    public ActorContext toActorContext() {
        Set<ActorContext.Role> roles = authorities.stream()
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .flatMap(value -> {
                    try {
                        return java.util.stream.Stream.of(
                                ActorContext.Role.valueOf(value)
                        );
                    } catch (IllegalArgumentException ignored) {
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toUnmodifiableSet());

        return new ActorContext(
                tenantId, producerId, actorId, actorType, identitySource,
                roles, permittedTenantIds, permittedResourceTypes
        );
    }

    public AuditRequestContext toRequestContext() {
        return new AuditRequestContext(
                tenantId, producerId, actorId, actorType, identitySource
        );
    }

    private static Set<String> claimStrings(Jwt jwt, String claimName) {
        Object value = jwt.getClaim(claimName);
        if (!(value instanceof Collection<?> values)) {
            return Set.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
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
            throw new IllegalStateException(
                    "Required trusted identity claim is missing: " + claim
            );
        }
        return value;
    }
}
