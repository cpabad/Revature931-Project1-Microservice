package com.revature.ers.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.revature.ers.auth.dto.LoginRequest;
import com.revature.ers.auth.model.Role;
import com.revature.ers.auth.model.User;
import com.revature.ers.auth.repository.UserRepository;
import com.revature.ers.auth.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises POST /login. The UserRepository is mocked so the test owns the stored hash: the seed's
 * bcrypt plaintexts are not recorded, and the live DB drifted (employee2 -> employee02). The real
 * PasswordEncoder and TokenService still run, so the bcrypt match and the signing are genuine.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @MockBean
    private UserRepository users;

    /** A seeded-looking Employee whose stored password is the real bcrypt hash of {@code raw}. */
    private User employeeWithPassword(String raw) {
        User user = new User();
        user.setUserId(2);
        user.setUsername("employee1");
        user.setPassword(passwordEncoder.encode(raw));
        user.setRole(new Role(2, "Employee"));
        return user;
    }

    private String body(String username, String password) throws Exception {
        return json.writeValueAsString(new LoginRequest(username, password));
    }

    @Test
    void login_goodCredentials_returnsToken() throws Exception {
        when(users.findByUsername("employee1")).thenReturn(Optional.of(employeeWithPassword("secret123")));

        mockMvc.perform(post("/login").contentType(APPLICATION_JSON).content(body("employee1", "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.role").value("Employee"));
    }

    @Test
    void login_wrongPassword_isUnauthorized() throws Exception {
        when(users.findByUsername("employee1")).thenReturn(Optional.of(employeeWithPassword("secret123")));

        mockMvc.perform(post("/login").contentType(APPLICATION_JSON).content(body("employee1", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unknownUser_isUnauthorized() throws Exception {
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/login").contentType(APPLICATION_JSON).content(body("ghost", "whatever")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_exactly72BytePassword_returnsToken() throws Exception {
        // Boundary pin for the CVE-2025-22228 guard: 72 bytes IS BCrypt's limit, not over it, so a
        // genuine 72-byte password must still authenticate (the guard rejects >72, not >=72).
        String pw = "a".repeat(72);
        when(users.findByUsername("employee1")).thenReturn(Optional.of(employeeWithPassword(pw)));

        mockMvc.perform(post("/login").contentType(APPLICATION_JSON).content(body("employee1", pw)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void login_over72BytePassword_sharedPrefix_isUnauthorized() throws Exception {
        // CVE-2025-22228: BCrypt reads only the first 72 bytes. The stored hash is of a 72-byte
        // password; a LONGER password sharing those 72 bytes would be truncated to the same input
        // and verify - the exact false-accept the vulnerability describes. The guard rejects the
        // oversized submission before it can reach matches(), yielding the same 401 as any bad login.
        String stored = "a".repeat(72);
        String oversizedSamePrefix = stored + "SUFFIX";
        when(users.findByUsername("employee1")).thenReturn(Optional.of(employeeWithPassword(stored)));

        mockMvc.perform(post("/login").contentType(APPLICATION_JSON).content(body("employee1", oversizedSamePrefix)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * End-to-end crypto: a token minted by the real TokenService is accepted by the real filter
     * chain + JwtDecoder on a secured route. This is the path the .with(jwt()) mock skips, so it
     * is what actually proves the issuer and the validator fit together. Since the split, this
     * service's secured surface is /users/me and /roles; GET /roles (Supervisor-only) also
     * exercises the real claim->authority mapping end to end. (The cross-SERVICE version of this
     * proof lives in reimbursement-service's AuthorizationRulesTest, which mints its own tokens.)
     */
    @Test
    void mintedToken_isAcceptedOnSecuredRoute() throws Exception {
        User supervisor = employeeWithPassword("irrelevant");
        supervisor.setRole(new Role(1, "Supervisor"));
        String token = tokenService.mint(supervisor);

        mockMvc.perform(get("/roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
