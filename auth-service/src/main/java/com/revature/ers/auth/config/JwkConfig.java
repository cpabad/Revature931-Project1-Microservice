package com.revature.ers.auth.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * The RS256 key material - the asymmetric replacement for the old shared HS256 secret.
 *
 * WHY RS256: with HS256 the same secret both signs and verifies, so every service that could
 * VERIFY a token could also MINT one - a compromised reimbursement-service could forge a
 * Supervisor identity. With RS256 the trust is one-way: this service signs with the PRIVATE
 * key (never leaves this JVM); everyone else fetches the PUBLIC key from /.well-known/jwks.json
 * and can verify but never forge. Verification rights no longer imply minting rights.
 *
 * KEY LIFECYCLE - two modes, chosen by {@code ers.jwt.private-key-pem}:
 *
 *  - UNSET (dev/test default): the pair is GENERATED AT STARTUP and held only in memory. A
 *    restart mints a new pair, which invalidates outstanding tokens - acceptable on a dev box
 *    (1h TTL), and no key file can ever be committed by accident. This mode is SINGLE-REPLICA
 *    ONLY: two replicas would each hold a different key, and a token minted by one would fail
 *    verification against the other's JWKS.
 *
 *  - SET to a PKCS#8 PEM path (production / k8s): the pair is LOADED from the file - in k8s, a
 *    Secret mounted into the pod and created out-of-band, exactly like the gitlab-registry pull
 *    secret (a private signing key never belongs in git or Tofu state):
 *        openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-signing.pem
 *        kubectl -n ers create secret generic auth-jwt-key --from-file=jwt-signing.pem
 *    Every replica loads the SAME key, and restarts keep outstanding tokens valid. A set-but-
 *    unloadable path FAILS THE BOOT - deliberately no fallback to ephemeral, because a typo'd
 *    mount silently downgrading production to per-replica keys is exactly the outage this mode
 *    exists to prevent (same fail-fast stance as hbm2ddl.auto=validate).
 *
 * In BOTH modes the kid is the RFC 7638 JWK thumbprint - a pure function of the key material -
 * so every replica loading the same PEM stamps the same kid, and verifiers match tokens to the
 * JWKS entry without any coordination. Rotation stays a JWKS story: serve old + new kid during
 * the overlap window; each token's kid header selects the right key at the verifier.
 */
@Configuration
public class JwkConfig {

    private static final Logger LOG = LoggerFactory.getLogger(JwkConfig.class);

    /** The keypair: loaded from the configured PEM, or generated fresh when none is configured. */
    @Bean
    public RSAKey rsaKey(@Value("${ers.jwt.private-key-pem:}") String privateKeyPemPath)
            throws Exception {
        if (privateKeyPemPath.isBlank()) {
            RSAKey generated = new RSAKeyGenerator(2048).generate();
            RSAKey withKid = withThumbprintKid(generated);
            LOG.info("JWT keys: EPHEMERAL RS256 pair generated (kid {}) - single-replica only; "
                    + "set ers.jwt.private-key-pem to load a persistent key", withKid.getKeyID());
            return withKid;
        }
        RSAKey loaded = loadRsaKey(Path.of(privateKeyPemPath));
        LOG.info("JWT keys: RS256 pair loaded from {} (kid {})", privateKeyPemPath, loaded.getKeyID());
        return loaded;
    }

    /**
     * Reads a PKCS#8 ("BEGIN PRIVATE KEY") PEM and rebuilds the full pair: an RSA private key in
     * CRT form carries the public modulus + exponent, so the public half is DERIVED, not stored -
     * one file in the Secret, no drift between the halves. Any failure (missing file, wrong
     * format) propagates and fails the boot on purpose - see the class comment.
     */
    static RSAKey loadRsaKey(Path pemPath) throws Exception {
        String pem = Files.readString(pemPath);
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        if (base64.isEmpty() || pem.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalStateException("ers.jwt.private-key-pem must be an unencrypted PKCS#8 "
                    + "PEM (BEGIN PRIVATE KEY); got something else in " + pemPath
                    + " - generate with: openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048");
        }
        KeyFactory rsa = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) rsa.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        RSAPublicKey publicKey = (RSAPublicKey) rsa.generatePublic(
                new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        return withThumbprintKid(new RSAKey.Builder(publicKey).privateKey(privateKey).build());
    }

    /** kid = RFC 7638 thumbprint: deterministic from the key bits, identical across replicas. */
    private static RSAKey withThumbprintKid(RSAKey key) throws JOSEException {
        return new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
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
