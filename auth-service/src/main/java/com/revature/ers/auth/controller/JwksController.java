package com.revature.ers.auth.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Publishes this issuer's PUBLIC key as a standard JWK Set. Verifiers (reimbursement-service,
 * and any future service) point NimbusJwtDecoder.withJwkSetUri at this endpoint instead of
 * sharing a secret. Serving only {@code toPublicJWK()} is the entire security property: the
 * private half never appears in any response, log, or config file.
 *
 * The path follows the RFC 8615 well-known convention so it reads like every real identity
 * provider's metadata endpoint. It is permitAll in SecurityConfig - public keys are public.
 */
@RestController
public class JwksController {

    private final Map<String, Object> jwks;

    public JwksController(RSAKey rsaKey) {
        // Computed once: the key is fixed for this process's lifetime (see JwkConfig).
        this.jwks = new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return jwks;
    }
}
