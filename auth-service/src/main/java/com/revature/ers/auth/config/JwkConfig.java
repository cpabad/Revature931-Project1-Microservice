package com.revature.ers.auth.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.util.UUID;

/**
 * The RS256 key material - the asymmetric replacement for the old shared HS256 secret.
 *
 * WHY RS256: with HS256 the same secret both signs and verifies, so every service that could
 * VERIFY a token could also MINT one - a compromised reimbursement-service could forge a
 * Supervisor identity. With RS256 the trust is one-way: this service signs with the PRIVATE
 * key (never leaves this JVM); everyone else fetches the PUBLIC key from /.well-known/jwks.json
 * and can verify but never forge. Verification rights no longer imply minting rights.
 *
 * KEY LIFECYCLE (deliberate dev tradeoff): the pair is GENERATED AT STARTUP and held only in
 * memory. A restart mints a new pair, which invalidates outstanding tokens - acceptable here
 * (1h TTL, dev box) and it means no key file can ever be committed by accident. Production
 * would load a persistent pair from a mounted PEM/secret store and rotate via the JWKS (serve
 * old + new kid during the overlap window); the kid in each token's header selects the right
 * key at the verifier.
 */
@Configuration
public class JwkConfig {

    /** The keypair. 2048-bit RSA; the kid ties tokens to this key generation in the JWKS. */
    @Bean
    public RSAKey rsaKey() throws JOSEException {
        return new RSAKeyGenerator(2048)
                .keyID(UUID.randomUUID().toString())
                .generate();
    }

    /** Signs with the PRIVATE half. Nimbus selects the key from the set and stamps its kid. */
    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    /** This service verifies its own tokens with the PUBLIC half - same as any other verifier. */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey) throws JOSEException {
        return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
    }
}
