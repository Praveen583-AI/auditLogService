package com.praveen.auditlog.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationPolicyTest {
    private final AuthorizationPolicy policy = new AuthorizationPolicy();

    @Test void tenantAndResourceScopeAreIndependentOfFrameworkAuthentication() {
        ActorContext reader = actor(ActorContext.Role.AUDIT_READER, Set.of("tenant-1"), Set.of("ACCOUNT"));
        assertThatCode(() -> policy.requireResourceAccess(reader, "tenant-1", "ACCOUNT"))
                .doesNotThrowAnyException();
        assertDenied(() -> policy.requireTenantAccess(reader, "tenant-2"), AuthorizationPolicy.Reason.TENANT_SCOPE_DENIED);
        assertDenied(() -> policy.requireResourceAccess(reader, "tenant-1", "PAYMENT"), AuthorizationPolicy.Reason.RESOURCE_SCOPE_DENIED);
    }

    @Test void complianceCanExportButCannotRunRetentionOrRedaction() {
        ActorContext compliance = actor(ActorContext.Role.COMPLIANCE_OFFICER, Set.of("tenant-1"), Set.of());
        assertThatCode(() -> policy.requirePrivilegedJob(compliance,
                AuthorizationPolicy.PrivilegedOperation.EXPORT, "tenant-1")).doesNotThrowAnyException();
        assertDenied(() -> policy.requirePrivilegedJob(compliance,
                AuthorizationPolicy.PrivilegedOperation.RETENTION, "tenant-1"), AuthorizationPolicy.Reason.ROLE_DENIED);
        assertDenied(() -> policy.requirePrivilegedJob(compliance,
                AuthorizationPolicy.PrivilegedOperation.REDACTION, "tenant-1"), AuthorizationPolicy.Reason.ROLE_DENIED);
    }

    @Test void administratorStillCannotEscapePermittedTenantScope() {
        ActorContext admin = actor(ActorContext.Role.AUDIT_ADMIN, Set.of("tenant-1"), Set.of());
        assertDenied(() -> policy.requirePrivilegedJob(admin,
                AuthorizationPolicy.PrivilegedOperation.RETENTION, "tenant-2"), AuthorizationPolicy.Reason.TENANT_SCOPE_DENIED);
        assertThatCode(() -> policy.requireCommitmentVerification(admin, "tenant-1"))
                .doesNotThrowAnyException();
    }

    private void assertDenied(Runnable action, AuthorizationPolicy.Reason reason) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(
                AuthorizationPolicy.AuthorizationDeniedException.class,
                failure -> org.assertj.core.api.Assertions.assertThat(failure.reason()).isEqualTo(reason));
    }

    private ActorContext actor(ActorContext.Role role, Set<String> tenants, Set<String> resources) {
        return new ActorContext("tenant-1", "producer-1", "actor-1", "USER", "JWT",
                Set.of(role), tenants, resources);
    }
}
