package com.praveen.auditlog.security;

import java.util.Set;

public record ActorContext(
        String tenantId,
        String producerId,
        String actorId,
        String actorType,
        String identitySource,
        Set<Role> roles,
        Set<String> permittedTenantIds,
        Set<String> permittedResourceTypes
) {
    public ActorContext {
        tenantId = required(tenantId, "tenantId");
        producerId = required(producerId, "producerId");
        actorId = required(actorId, "actorId");
        actorType = required(actorType, "actorType");
        identitySource = required(identitySource, "identitySource");
        roles = Set.copyOf(roles);
        permittedTenantIds = Set.copyOf(permittedTenantIds);
        permittedResourceTypes = Set.copyOf(permittedResourceTypes);
        if (!permittedTenantIds.contains(tenantId)) {
            throw new IllegalArgumentException(
                    "The authenticated tenant must be inside the permitted tenant scope"
            );
        }
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean permitsTenant(String candidateTenantId) {
        return permittedTenantIds.contains(candidateTenantId);
    }

    public boolean permitsResourceType(String resourceType) {
        return permittedResourceTypes.isEmpty()
                || permittedResourceTypes.contains(resourceType);
    }

    public enum Role {
        AUDIT_WRITER,
        AUDIT_READER,
        COMPLIANCE_OFFICER,
        AUDIT_ADMIN
    }

    @FunctionalInterface
    public interface Provider {
        ActorContext currentActor();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
