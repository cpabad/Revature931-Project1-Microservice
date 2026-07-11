package com.revature.ers.controller;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The role rules end-to-end with REAL signed tokens - and, since the split, a cross-service
 * contract test: this service has NO JwtEncoder of its own, so the test generates an RSA
 * keypair and plays the part of auth-service, signing RS256 with the PRIVATE half. The
 * {@code @Primary} decoder below verifies with only the PUBLIC half - exactly the asymmetry
 * production runs on (production fetches the same public key over the JWKS endpoint; the test
 * injects it directly to keep MockMvc free of HTTP fetches). A pass proves a token minted
 * OUTSIDE this service validates here, and that the JwtAuthenticationConverter maps the
 * "role" claim to ROLE_* - the linchpin the path rules rely on.
 *
 * Note what this test can NO LONGER do that its HS256 predecessor could: mint with the
 * verifier's own key material taken from config. That capability gap IS the RS256 upgrade.
 *
 *   401 Unauthorized = "who are you?"  (no/invalid token - stopped in the filter chain)
 *   403 Forbidden    = "I know who you are, you're not allowed" (valid token, wrong role)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthorizationRulesTest.IssuerStandIn.class)
class AuthorizationRulesTest {

    /** The test's own keypair - the stand-in for auth-service's JwkConfig. */
    private static final RSAKey TEST_KEY;
    static {
        try {
            TEST_KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("test RSA keypair generation failed", e);
        }
    }

    @TestConfiguration
    static class IssuerStandIn {
        /** Replaces the JWKS-fetching decoder: same public-key verification, no HTTP. */
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() throws JOSEException {
            return NimbusJwtDecoder.withPublicKey(TEST_KEY.toRSAPublicKey()).build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private String mint(int userId, String role) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(TEST_KEY)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ers-service")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .subject(String.valueOf(userId))
                .claim("role", role)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    // --- path rule on GET /requests (Supervisor-only) -----------------------------------------

    @Test
    void supervisor_canListAllRequests() throws Exception {
        mockMvc.perform(get("/requests").header("Authorization", "Bearer " + mint(1, "Supervisor")))
                .andExpect(status().isOk());
    }

    @Test
    void employee_cannotListAllRequests_isForbidden() throws Exception {
        mockMvc.perform(get("/requests").header("Authorization", "Bearer " + mint(2, "Employee")))
                .andExpect(status().isForbidden());
    }

    @Test
    void noToken_onSupervisorRoute_isUnauthorized() throws Exception {
        mockMvc.perform(get("/requests"))
                .andExpect(status().isUnauthorized());
    }

    // --- object-level authorization: /requests/{id} is owner-or-Supervisor ---------------------
    // Seed fact: request 1 belongs to userId 2. A foreign employee gets 404, not 403 - a 403
    // would confirm the id exists, handing an id-walker an enumeration oracle.

    @Test
    void owner_canReadOwnRequestById() throws Exception {
        mockMvc.perform(get("/requests/1").header("Authorization", "Bearer " + mint(2, "Employee")))
                .andExpect(status().isOk());
    }

    @Test
    void otherEmployee_readingForeignRequestById_isNotFound() throws Exception {
        mockMvc.perform(get("/requests/1").header("Authorization", "Bearer " + mint(3, "Employee")))
                .andExpect(status().isNotFound());
    }

    @Test
    void supervisor_canReadAnyRequestById() throws Exception {
        mockMvc.perform(get("/requests/1").header("Authorization", "Bearer " + mint(1, "Supervisor")))
                .andExpect(status().isOk());
    }

    // --- /requests/requester/{userId}: that user or a Supervisor -------------------------------
    // Mismatch is an honest 403 here: the caller supplied the userId, nothing to leak.

    @Test
    void employee_canViewOwnRequestsByRequester() throws Exception {
        mockMvc.perform(get("/requests/requester/2").header("Authorization", "Bearer " + mint(2, "Employee")))
                .andExpect(status().isOk());
    }

    @Test
    void employee_viewingAnotherUsersRequests_isForbidden() throws Exception {
        mockMvc.perform(get("/requests/requester/2").header("Authorization", "Bearer " + mint(3, "Employee")))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisor_canViewAnyUsersRequestsByRequester() throws Exception {
        mockMvc.perform(get("/requests/requester/2").header("Authorization", "Bearer " + mint(1, "Supervisor")))
                .andExpect(status().isOk());
    }
}
