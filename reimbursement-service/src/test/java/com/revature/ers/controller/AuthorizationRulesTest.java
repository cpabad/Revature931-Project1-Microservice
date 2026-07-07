package com.revature.ers.controller;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The role rules end-to-end with REAL signed tokens - and, since the split, a cross-service
 * contract test: this service has NO JwtEncoder of its own, so the test builds one from the
 * same shared secret, playing the part of auth-service. A pass proves a token minted OUTSIDE
 * this service validates here (HS256 symmetry) and that the JwtAuthenticationConverter maps
 * the "role" claim to ROLE_* - the linchpin the path rules rely on.
 *
 *   401 Unauthorized = "who are you?"  (no/invalid token - stopped in the filter chain)
 *   403 Forbidden    = "I know who you are, you're not allowed" (valid token, wrong role)
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationRulesTest {

    @Autowired
    private MockMvc mockMvc;

    /** The shared HS256 secret - the same value auth-service signs with. */
    @Value("${ers.jwt.secret}")
    private String jwtSecret;

    private String mint(int userId, String role) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ers-service")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .subject(String.valueOf(userId))
                .claim("role", role)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
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

    // --- shared route: any authenticated role may view requests by requester -------------------

    @Test
    void employee_canViewRequestsByRequester() throws Exception {
        mockMvc.perform(get("/requests/requester/2").header("Authorization", "Bearer " + mint(2, "Employee")))
                .andExpect(status().isOk());
    }
}
