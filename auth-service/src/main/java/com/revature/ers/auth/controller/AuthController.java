package com.revature.ers.auth.controller;

import com.revature.ers.auth.dto.LoginRequest;
import com.revature.ers.auth.dto.LoginResponse;
import com.revature.ers.auth.repository.UserRepository;
import com.revature.ers.auth.service.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * The issuer endpoint. Replaces the monolith's session-creating login: instead of populating an
 * HttpSession, a successful login returns a signed JWT the client carries on every later request.
 *
 * The whole flow is one Optional pipeline:
 *   find the user -> keep only if the password matches -> mint a token -> 200
 *   anything missing (no such user OR wrong password) -> the same 401.
 *
 * Returning ONE 401 for both "unknown user" and "bad password" is deliberate: distinct messages
 * would let an attacker enumerate valid usernames. This is open (permitAll) in SecurityConfig -
 * you cannot hold a token before you log in.
 */
@RestController
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest credentials) {
        // Maximum password length established per CVE-2025-22228: BCrypt reads only the first 72
        // bytes of its input, so matches() would return true for ANY longer password whose first
        // 72 bytes match a stored hash. Reject oversized input up front - the fixed library (Spring
        // Security 6.5.x via the Boot 3.5 bump) closes the hole, this is defense in depth. Same
        // empty 401 as any bad credential: the endpoint never says WHY it rejected you.
        // https://avd.aquasec.com/nvd/cve-2025-22228
        if (exceedsBCrypt72ByteLimit(credentials.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return users.findByUsername(credentials.username())
                // BCrypt verify: hash the submitted password with the stored salt and compare -
                // never compare raw text, never decrypt (bcrypt is one-way).
                .filter(user -> passwordEncoder.matches(credentials.password(), user.getPassword()))
                .map(user -> ResponseEntity.ok(new LoginResponse(
                        tokenService.mint(user),
                        "Bearer",
                        user.getUserId(),
                        user.getRole().getRole(),
                        tokenService.getTtlSeconds())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    // BCrypt reads at most 72 bytes; spring-security-crypto silently truncates anything beyond
    // that (CVE-2025-22228). Byte length, not char length - a multi-byte UTF-8 character counts
    // once per byte, so the limit is reached sooner than the character count suggests.
    private static boolean exceedsBCrypt72ByteLimit(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length > 72;
    }
}
