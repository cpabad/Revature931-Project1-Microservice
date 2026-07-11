package com.revature.ers.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security for the reimbursement service: a PURE resource server. It has no /login and no
 * JwtEncoder - auth-service mints the tokens; this service verifies RS256 signatures with
 * auth-service's PUBLIC key, fetched from its JWKS endpoint. Since the RS256 move, "pure
 * resource server" is enforced by cryptography, not just by code shape: holding the public
 * key lets this service verify tokens but never mint them - a compromise here cannot forge
 * an identity. (Under the old shared HS256 secret it could.)
 */
@Configuration
public class SecurityConfig {

    /** Where auth-service publishes its public key set (env-driven for compose/K8s). */
    private final String jwksUri;

    public SecurityConfig(@Value("${ers.jwt.jwks-uri}") String jwksUri) {
        this.jwksUri = jwksUri;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // listing EVERY employee's requests is a supervisor-only capability
                .requestMatchers(HttpMethod.GET, "/requests").hasRole("Supervisor")
                // the approval chain is a supervisor capability: deciding a request and the
                // pending-votes inbox. (POST /requests stays under the catch-all - any
                // authenticated user may submit for themselves.)
                .requestMatchers(HttpMethod.PUT, "/requests/*/approval").hasRole("Supervisor")
                .requestMatchers("/approvals/**").hasRole("Supervisor")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Verifies tokens against the issuer's JWKS. The key set is fetched LAZILY on the first
     * token validation (then cached by Nimbus), so this service starts fine with auth-service
     * down - but cannot validate its first bearer request until the issuer is reachable.
     * That startup-order looseness is the practical win of JWKS over baking the public key
     * into config; the token header's kid picks the matching key after a rotation.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
    }

    /** Same claim mapping as auth-service: "role" claim -> ROLE_* authority. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
