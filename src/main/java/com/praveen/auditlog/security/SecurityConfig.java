package com.praveen.auditlog.security;

import com.praveen.auditlog.application.AuditRequestContext;
import com.praveen.auditlog.application.AuditRequestContextProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(
            name = "audit.security.enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    static class TrustedBoundaryConfiguration {

        @Bean
        JwtDecoder auditJwtDecoder(
                @Value("${audit.security.issuer-uri}") String issuerUri
        ) {
            return JwtDecoders.fromIssuerLocation(issuerUri);
        }

        @Bean
        SecurityFilterChain trustedApiSecurity(
                HttpSecurity http,
                JwtDecoder auditJwtDecoder
        ) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/actuator/health", "/actuator/health/**")
                            .permitAll()
                            .requestMatchers(HttpMethod.POST, "/v1/audit/events")
                            .hasAuthority("SCOPE_audit.write")
                            .requestMatchers(HttpMethod.GET, "/v1/audit/events/**")
                            .hasAuthority("SCOPE_audit.read")
                            .requestMatchers("/internal/**")
                            .hasAuthority("SCOPE_audit.internal")
                            .requestMatchers("/admin/**")
                            .hasAuthority("SCOPE_audit.admin")
                            .anyRequest().denyAll())
                    .oauth2ResourceServer(resourceServer -> resourceServer
                            .jwt(jwt -> jwt.decoder(auditJwtDecoder)))
                    .build();
        }

        @Bean
        AuditRequestContextProvider authenticatedRequestContextProvider() {
            return () -> AuthenticatedActor.from(
                    SecurityContextHolder.getContext().getAuthentication()
            ).toRequestContext();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(
            name = "audit.security.enabled",
            havingValue = "false"
    )
    static class SimplifiedSecurityConfiguration {

        @Bean
        SecurityFilterChain simplifiedSecurity(HttpSecurity http)
                throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize
                            .anyRequest().permitAll())
                    .httpBasic(Customizer.withDefaults())
                    .build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("local")
    @ConditionalOnProperty(
            name = "audit.security.enabled",
            havingValue = "false"
    )
    static class LocalIdentityConfiguration {

        @Bean
        AuditRequestContextProvider localRequestContextProvider(
                @Value("${audit.local-identity.tenant-id}") String tenantId,
                @Value("${audit.local-identity.producer-id}") String producerId,
                @Value("${audit.local-identity.actor-id}") String actorId,
                @Value("${audit.local-identity.actor-type:DEVELOPER}") String actorType
        ) {
            AuditRequestContext context = new AuditRequestContext(
                    tenantId, producerId, actorId, actorType,
                    "LOCAL_PROFILE_ONLY"
            );
            return () -> context;
        }
    }
}
