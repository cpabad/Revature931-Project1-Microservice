package com.revature.ers.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security wiring for the ERS microservice - replaces the monolith's three hand-rolled
 * servlet filters (SessionFilter / EmployeeFilter / ManagerFilter) with declarative config.
 *
 * This class is the VALIDATION side of the JWT story (Step 1). The minting side (JwtEncoder +
 * /login) arrives in Step 2; role rules in Step 3.
 *
 * How a request flows now:
 *   1. OAuth2 Resource Server's BearerTokenAuthenticationFilter pulls the token out of the
 *      "Authorization: Bearer <jwt>" header.
 *   2. Our JwtDecoder bean verifies the HS256 signature + expiry (throws -> 401 if bad).
 *   3. Our JwtAuthenticationConverter turns the validated claims into an Authentication, mapping
 *      the "role" claim to a Spring authority. That Authentication lands in the SecurityContext.
 *   4. authorizeHttpRequests decides allow/deny. Right now: everything needs a valid token.
 */
@Configuration
// Turns on @PreAuthorize/@PostAuthorize so role rules can also live as annotations on methods
// (mechanism #2). prePostEnabled is true by default - this line is what makes RoleController's
// @PreAuthorize actually enforced rather than silently ignored.
@EnableMethodSecurity
public class SecurityConfig {

    /** Shared HMAC secret (see application.properties). Same key mints and verifies. */
    private final SecretKey jwtKey;

    public SecurityConfig(@Value("${ers.jwt.secret}") String secret) {
        // HS256 = HMAC-SHA-256. The "HmacSHA256" JCA name ties this raw-bytes key to that algo.
        this.jwtKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF guards COOKIE/SESSION auth against forged cross-site form posts. Our auth is a
            // Bearer token the browser does NOT attach automatically cross-site, so CSRF is moot
            // for this stateless API - disabling it is the standard, correct choice here.
            .csrf(AbstractHttpConfigurer::disable)
            // No HttpSession at all: the JWT carries identity on every request. This is the
            // literal death of the monolith's HttpSession - the token replaces it.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Authorization rules, evaluated top-to-bottom; first match wins. /login must be open
            // (you cannot present a token before you have one - the classic bootstrap carve-out,
            // mirroring the monolith filters' "login URLs are always exempt"). Everything else
            // still needs a valid token. Role rules (Supervisor vs Employee) arrive in Step 3.
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/login").permitAll()
                // Mechanism #1 - path rule, the direct heir to the monolith's /manager/* filter:
                // listing EVERY employee's requests is a supervisor-only capability. hasRole(
                // "Supervisor") checks for the ROLE_Supervisor authority our converter produces.
                // (Order matters - this more specific GET rule sits ABOVE the catch-all below.)
                .requestMatchers(HttpMethod.GET, "/requests").hasRole("Supervisor")
                // The approval chain is a supervisor capability: deciding a request and
                // reading the pending-votes inbox. (POST /requests stays under the
                // catch-all - any authenticated user may submit for themselves.)
                .requestMatchers(HttpMethod.PUT, "/requests/*/approval").hasRole("Supervisor")
                .requestMatchers("/approvals/**").hasRole("Supervisor")
                .anyRequest().authenticated())
            // Turn on JWT validation, handing Spring our decoder + claim->authority converter.
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Validates incoming tokens. NimbusJwtDecoder.withSecretKey checks the HMAC signature with
     * our key and (by default) rejects expired tokens. A failed check becomes a 401 automatically.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(jwtKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Mints (signs) tokens with the SAME secret the decoder verifies with - that symmetry is the
     * whole point of HS256 for a self-issuing service. ImmutableSecret wraps our key as the JWK
     * source Nimbus signs from. (Used by TokenService when /login succeeds.)
     */
    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    /**
     * BCrypt verifier/hasher - same algorithm the monolith used (its seed stores $2a$ hashes), so
     * the existing password column works unchanged. /login calls matches(raw, storedHash); future
     * password writes would call encode(...).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Maps our custom "role" claim (a single string like "Employee"/"Supervisor") to a Spring
     * authority. THE GOTCHA: Spring's hasRole('X') checks for the authority "ROLE_X" - the prefix
     * is implicit in hasRole but must be present on the granted authority. So we read the "role"
     * claim and prefix it with "ROLE_", yielding e.g. ROLE_Employee. (Spring's default converter
     * reads "scope"/"scp" with a SCOPE_ prefix, which is not our shape - hence this override.)
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
