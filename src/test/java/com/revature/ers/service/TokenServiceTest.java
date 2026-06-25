package com.revature.ers.service;

import com.revature.ers.model.Role;
import com.revature.ers.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves mint -> verify with the REAL beans: TokenService signs with the JwtEncoder, and the very
 * same JwtDecoder the resource server uses on live requests verifies the signature and reads the
 * claims back. If the signature, algorithm, or secret were wrong, decode() would throw.
 */
@SpringBootTest
class TokenServiceTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void mint_thenDecode_roundTripsClaimsAndVerifiesSignature() {
        User user = new User();
        user.setUserId(2);
        user.setRole(new Role(2, "Employee"));

        String token = tokenService.mint(user);
        Jwt decoded = jwtDecoder.decode(token);   // throws if signature/expiry invalid

        assertThat(decoded.getSubject()).isEqualTo("2");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("Employee");
        // iss is read as a String (not getIssuer(), which would try to parse it as a URL).
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("ers-service");
        assertThat(decoded.getExpiresAt()).isAfter(Instant.now());
    }
}
