package com.revature.ers.service;

import com.revature.ers.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Mints the self-issued ERS JWT. This is the ONE place a token is created; AuthController calls it
 * only after the password check passes.
 *
 * The claim shape (mirrors what the monolith stashed in the HttpSession):
 *   - sub  = userId          (the subject - "who is this token about")
 *   - role = "Employee"/"Supervisor"  (drives authorization; SecurityConfig maps it to ROLE_*)
 *   - iss  = "ers-service"   (issuer - we are our own issuer)
 *   - iat/exp                (issued-at / expiry; the decoder rejects an expired token)
 */
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final long ttlSeconds;

    public TokenService(JwtEncoder jwtEncoder,
                        @Value("${ers.jwt.ttl-seconds}") long ttlSeconds) {
        this.jwtEncoder = jwtEncoder;
        this.ttlSeconds = ttlSeconds;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public String mint(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ers-service")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .subject(String.valueOf(user.getUserId()))
                .claim("role", user.getRole().getRole())
                .build();
        // HS256 must be named explicitly in the header so it matches the decoder's expectation.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
