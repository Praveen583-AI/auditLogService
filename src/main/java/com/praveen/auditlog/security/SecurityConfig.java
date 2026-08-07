package com.praveen.auditlog.security;

import com.praveen.auditlog.application.AuditRequestContext;
import com.praveen.auditlog.application.AuditRequestContextProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;

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
        Converter<Jwt, ? extends AbstractAuthenticationToken>
        auditJwtAuthenticationConverter() {
            JwtGrantedAuthoritiesConverter scopes =
                    new JwtGrantedAuthoritiesConverter();
            JwtGrantedAuthoritiesConverter roles =
                    new JwtGrantedAuthoritiesConverter();
            roles.setAuthoritiesClaimName("roles");
            roles.setAuthorityPrefix("ROLE_");

            JwtAuthenticationConverter converter =
                    new JwtAuthenticationConverter();
            converter.setJwtGrantedAuthoritiesConverter(jwt -> {
                Collection<GrantedAuthority> authorities = new ArrayList<>();
                Collection<GrantedAuthority> scopeAuthorities =
                        scopes.convert(jwt);
                Collection<GrantedAuthority> roleAuthorities =
                        roles.convert(jwt);
                if (scopeAuthorities != null) {
                    authorities.addAll(scopeAuthorities);
                }
                if (roleAuthorities != null) {
                    authorities.addAll(roleAuthorities);
                }
                return authorities;
            });
            return converter;
        }

        @Bean
        SecurityFilterChain trustedApiSecurity(
                HttpSecurity http,
                JwtDecoder auditJwtDecoder,
                Converter<Jwt, ? extends
                        AbstractAuthenticationToken>
                        auditJwtAuthenticationConverter
        ) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/actuator/health", "/actuator/health/**")
                            .permitAll()
                            .requestMatchers(HttpMethod.POST, "/v1/audit/events")
                            .hasRole("AUDIT_WRITER")
                            .requestMatchers(HttpMethod.GET, "/v1/audit/events/**")
                            .hasAnyRole(
                                    "AUDIT_READER",
                                    "COMPLIANCE_OFFICER",
                                    "AUDIT_ADMIN"
                            )
                            .requestMatchers("/internal/retention/**",
                                    "/internal/redaction/**")
                            .hasRole("AUDIT_ADMIN")
                            .requestMatchers("/internal/export/**",
                                    "/admin/regulator-reports/**")
                            .hasAnyRole("COMPLIANCE_OFFICER", "AUDIT_ADMIN")
                            .requestMatchers("/internal/**", "/admin/**")
                            .hasRole("AUDIT_ADMIN")
                            .anyRequest().denyAll())
                    .oauth2ResourceServer(resourceServer -> resourceServer
                            .jwt(jwt -> jwt
                                    .decoder(auditJwtDecoder)
                                    .jwtAuthenticationConverter(
                                            auditJwtAuthenticationConverter
                                    )))
                    .build();
        }

        @Bean
        ActorContext.Provider actorContextProvider() {
            return () -> AuthenticatedActor.from(
                    SecurityContextHolder.getContext().getAuthentication()
            ).toActorContext();
        }

        @Bean
        AuditRequestContextProvider authenticatedRequestContextProvider(
                ActorContext.Provider actors
        ) {
            return () -> {
                ActorContext actor = actors.currentActor();
                return new AuditRequestContext(
                        actor.tenantId(),
                        actor.producerId(),
                        actor.actorId(),
                        actor.actorType(),
                        actor.identitySource()
                );
            };
        }

        @Bean
        AuthorizationPolicy authorizationPolicy() {
            return new AuthorizationPolicy();
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
        ActorContext.Provider localActorContextProvider(
                @Value("${audit.local-identity.tenant-id}") String tenantId,
                @Value("${audit.local-identity.producer-id}") String producerId,
                @Value("${audit.local-identity.actor-id}") String actorId,
                @Value("${audit.local-identity.actor-type:DEVELOPER}") String actorType
        ) {
            ActorContext context = new ActorContext(
                    tenantId, producerId, actorId, actorType,
                    "LOCAL_PROFILE_ONLY",
                    java.util.Set.of(
                            ActorContext.Role.AUDIT_WRITER,
                            ActorContext.Role.AUDIT_READER
                    ),
                    java.util.Set.of(tenantId),
                    java.util.Set.of()
            );
            return () -> context;
        }

        @Bean
        AuditRequestContextProvider localRequestContextProvider(
                ActorContext.Provider actors
        ) {
            return () -> {
                ActorContext actor = actors.currentActor();
                return new AuditRequestContext(
                        actor.tenantId(), actor.producerId(),
                        actor.actorId(), actor.actorType(),
                        actor.identitySource()
                );
            };
        }

        @Bean
        AuthorizationPolicy localAuthorizationPolicy() {
            return new AuthorizationPolicy();
        }
    }
}
