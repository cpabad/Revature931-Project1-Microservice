package com.revature.ers.controller;

import com.revature.ers.dto.LoginRequest;
import com.revature.ers.dto.LoginResponse;
import com.revature.ers.repository.UserRepository;
import com.revature.ers.service.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
}
