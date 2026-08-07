package com.praveen.auditlog.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SecurityIntegrationTest.ProbeController.class,
        properties = {
                "audit.security.enabled=true",
                "audit.security.issuer-uri=https://issuer.example"
        }
)
@Import({SecurityConfig.class, SecurityIntegrationTest.ProbeController.class})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder auditJwtDecoder;

    @Test
    void missingTokenIsRejected() throws Exception {
        mvc.perform(get("/v1/audit/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void writerCanWriteButReaderCannot() throws Exception {
        mvc.perform(post("/v1/audit/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(role("AUDIT_WRITER")))
                .andExpect(status().isOk());

        mvc.perform(post("/v1/audit/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(role("AUDIT_READER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void readRoleMatrixIsEnforced() throws Exception {
        for (String role : Set.of(
                "AUDIT_READER", "COMPLIANCE_OFFICER", "AUDIT_ADMIN"
        )) {
            mvc.perform(get("/v1/audit/events").with(role(role)))
                    .andExpect(status().isOk());
        }

        mvc.perform(get("/v1/audit/events")
                        .with(role("AUDIT_WRITER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void privilegedJobsRejectNormalReader() throws Exception {
        mvc.perform(post("/internal/retention/run")
                        .with(role("AUDIT_READER")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/internal/export/run")
                        .with(role("AUDIT_READER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void privilegedRoleMatrixSeparatesExportFromRetention()
            throws Exception {
        mvc.perform(post("/internal/export/run")
                        .with(role("COMPLIANCE_OFFICER")))
                .andExpect(status().isOk());

        mvc.perform(post("/internal/retention/run")
                        .with(role("COMPLIANCE_OFFICER")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/internal/retention/run")
                        .with(role("AUDIT_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void policyRejectsWrongTenantAndResourceScope() {
        ActorContext reader = actor(
                ActorContext.Role.AUDIT_READER,
                Set.of("tenant-a"),
                Set.of("ACCOUNT")
        );
        AuthorizationPolicy policy = new AuthorizationPolicy();

        assertThatThrownBy(() ->
                policy.requireTenantAccess(reader, "tenant-b"))
                .isInstanceOf(
                        AuthorizationPolicy.AuthorizationDeniedException.class
                );
        assertThatThrownBy(() ->
                policy.requireResourceAccess(
                        reader, "tenant-a", "PAYMENT"))
                .isInstanceOf(
                        AuthorizationPolicy.AuthorizationDeniedException.class
                );
    }

    @Test
    void policyRejectsReaderAndOutOfScopeAdminForPrivilegedJob() {
        AuthorizationPolicy policy = new AuthorizationPolicy();

        assertThatThrownBy(() -> policy.requirePrivilegedJob(
                actor(ActorContext.Role.AUDIT_READER,
                        Set.of("tenant-a"), Set.of()),
                AuthorizationPolicy.PrivilegedOperation.RETENTION,
                "tenant-a"
        )).isInstanceOf(
                AuthorizationPolicy.AuthorizationDeniedException.class
        );

        assertThatThrownBy(() -> policy.requirePrivilegedJob(
                actor(ActorContext.Role.AUDIT_ADMIN,
                        Set.of("tenant-a"), Set.of()),
                AuthorizationPolicy.PrivilegedOperation.RETENTION,
                "tenant-b"
        )).isInstanceOf(
                AuthorizationPolicy.AuthorizationDeniedException.class
        );
    }

    private org.springframework.test.web.servlet.request
            .RequestPostProcessor role(String role) {
        return jwt().authorities(
                new SimpleGrantedAuthority("ROLE_" + role)
        );
    }

    private ActorContext actor(
            ActorContext.Role role,
            Set<String> tenants,
            Set<String> resourceTypes
    ) {
        return new ActorContext(
                "tenant-a", "producer-1", "actor-1", "USER",
                "TEST", Set.of(role), tenants, resourceTypes
        );
    }

    @RestController
    public static class ProbeController {

        @GetMapping("/v1/audit/events")
        String query() {
            return "ok";
        }

        @PostMapping("/v1/audit/events")
        String write() {
            return "ok";
        }

        @PostMapping("/internal/retention/run")
        String retention() {
            return "ok";
        }

        @PostMapping("/internal/export/run")
        String export() {
            return "ok";
        }
    }
}
