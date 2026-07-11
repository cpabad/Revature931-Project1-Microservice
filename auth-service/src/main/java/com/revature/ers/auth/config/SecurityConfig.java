package com.revature.ers.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

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

    // Key material, JwtEncoder, and JwtDecoder live in JwkConfig since the RS256 move: this
    // service signs with a private key and publishes the public half at /.well-known/jwks.json.

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
            // this service owns - /users/me, /roles - needs a valid token; /roles additionally
            // carries @PreAuthorize (mechanism #2). The reimbursement routes and their path
            // rules (mechanism #1) live in ers-reimbursement-service's own SecurityConfig.
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/login").permitAll()
                // The JWKS is the PUBLIC key - it must be fetchable by verifiers (and anyone)
                // without a token, or no service could ever validate its first request.
                .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
                .anyRequest().authenticated())
            // Turn on JWT validation, handing Spring our decoder + claim->authority converter.
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
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
