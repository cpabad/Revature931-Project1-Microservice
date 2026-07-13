package com.revature.ers.auth.config;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The persistent-key half of JwkConfig: loading a PKCS#8 PEM must yield the SAME key (and the
 * same RFC 7638 thumbprint kid) every time - that determinism is the whole point, it's what
 * lets two replicas or a restarted pod keep honoring each other's tokens. The ephemeral half
 * needs no test of its own here: every existing @SpringBootTest boots through it.
 */
class JwkConfigTest {

    @TempDir
    Path tempDir;

    /** openssl-genpkey shape without shelling out: PKCS#8 DER from JCA, wrapped in PEM armor. */
    private Path writePkcs8Pem(KeyPair pair, String filename) throws Exception {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Path file = tempDir.resolve(filename);
        Files.writeString(file, pem);
        return file;
    }

    private KeyPair generateRsaPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    @Test
    void loadsPemAndDerivesMatchingPublicHalf() throws Exception {
        KeyPair pair = generateRsaPair();
        Path pem = writePkcs8Pem(pair, "jwt-signing.pem");

        RSAKey loaded = JwkConfig.loadRsaKey(pem);

        // The public half is DERIVED from the private key's CRT params - it must equal the
        // public key JCA generated alongside, or verifiers would reject every signature.
        assertEquals(pair.getPublic(), loaded.toRSAPublicKey());
        assertTrue(loaded.isPrivate(), "loaded key must carry the signing (private) half");
    }

    @Test
    void kidIsDeterministicAcrossLoads() throws Exception {
        KeyPair pair = generateRsaPair();
        Path pem = writePkcs8Pem(pair, "jwt-signing.pem");

        RSAKey firstBoot = JwkConfig.loadRsaKey(pem);
        RSAKey secondBoot = JwkConfig.loadRsaKey(pem);

        // Same file -> same kid on every boot and every replica: a token minted before a
        // restart still matches the JWKS entry served after it.
        assertEquals(firstBoot.getKeyID(), secondBoot.getKeyID());
        assertEquals(firstBoot.computeThumbprint().toString(), firstBoot.getKeyID(),
                "kid must be the RFC 7638 thumbprint, not a random UUID");
    }

    @Test
    void differentKeysGetDifferentKids() throws Exception {
        RSAKey a = JwkConfig.loadRsaKey(writePkcs8Pem(generateRsaPair(), "a.pem"));
        RSAKey b = JwkConfig.loadRsaKey(writePkcs8Pem(generateRsaPair(), "b.pem"));
        assertTrue(!a.getKeyID().equals(b.getKeyID()));
    }

    @Test
    void missingFileFailsTheBoot() {
        // Set-but-unloadable must throw, not fall back to an ephemeral pair - a typo'd Secret
        // mount silently downgrading to per-replica keys is the outage this mode prevents.
        assertThrows(NoSuchFileException.class,
                () -> JwkConfig.loadRsaKey(tempDir.resolve("nope.pem")));
    }

    @Test
    void pkcs1PemIsRejectedWithAnActionableMessage() throws Exception {
        // openssl genrsa (legacy) emits PKCS#1 "BEGIN RSA PRIVATE KEY" - a different DER shape
        // PKCS8EncodedKeySpec cannot parse. Fail with the command that produces the right format.
        Path pkcs1 = tempDir.resolve("legacy.pem");
        Files.writeString(pkcs1, "-----BEGIN RSA PRIVATE KEY-----\nMIIB\n-----END RSA PRIVATE KEY-----\n");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> JwkConfig.loadRsaKey(pkcs1));
        assertTrue(ex.getMessage().contains("openssl genpkey"));
    }
}
