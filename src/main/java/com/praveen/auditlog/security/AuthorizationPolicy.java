package com.praveen.auditlog.security;

import java.util.Objects;

public final class AuthorizationPolicy {

    public void requireTenantAccess(
            ActorContext actor,
            String targetTenantId
    ) {
        Objects.requireNonNull(actor, "actor");
        if (targetTenantId == null
                || !actor.permitsTenant(targetTenantId)) {
            throw new AuthorizationDeniedException(
                    Reason.TENANT_SCOPE_DENIED
            );
        }
    }

    public void requireResourceAccess(
            ActorContext actor,
            String targetTenantId,
            String resourceType
    ) {
        requireTenantAccess(actor, targetTenantId);
        if (resourceType == null
                || !actor.permitsResourceType(resourceType)) {
            throw new AuthorizationDeniedException(
                    Reason.RESOURCE_SCOPE_DENIED
            );
        }
    }

    public void requirePrivilegedJob(
            ActorContext actor,
            PrivilegedOperation operation,
            String targetTenantId
    ) {
        requireTenantAccess(actor, targetTenantId);
        boolean allowed = switch (operation) {
            case EXPORT, REGULATOR_REPORT ->
                    actor.hasRole(ActorContext.Role.COMPLIANCE_OFFICER)
                            || actor.hasRole(ActorContext.Role.AUDIT_ADMIN);
            case RETENTION, REDACTION ->
                    actor.hasRole(ActorContext.Role.AUDIT_ADMIN);
        };
        if (!allowed) {
            throw new AuthorizationDeniedException(
                    Reason.ROLE_DENIED
            );
        }
    }

    public void requireCommitmentVerification(ActorContext actor, String targetTenantId) {
        requireTenantAccess(actor, targetTenantId);
        if (!actor.hasRole(ActorContext.Role.COMPLIANCE_OFFICER)
                && !actor.hasRole(ActorContext.Role.AUDIT_ADMIN)) {
            throw new AuthorizationDeniedException(Reason.ROLE_DENIED);
        }
    }

    public enum PrivilegedOperation {
        EXPORT,
        RETENTION,
        REDACTION,
        REGULATOR_REPORT
    }

    public enum Reason {
        ROLE_DENIED,
        TENANT_SCOPE_DENIED,
        RESOURCE_SCOPE_DENIED
    }

    public static final class AuthorizationDeniedException
            extends RuntimeException {

        private final Reason reason;

        AuthorizationDeniedException(Reason reason) {
            super("Authorization policy denied the operation");
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }
}
