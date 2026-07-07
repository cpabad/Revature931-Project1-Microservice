package com.revature.ers.controller;

import com.revature.ers.model.User;
import com.revature.ers.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PUT /users/me end-to-end. The seed's bcrypt plaintexts are unrecorded, so each test first
 * stamps user 3 with a hash of a KNOWN password inside the test transaction (rolled back after)
 * - the service then reads that row through the same transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    private static final String KNOWN_PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void stampKnownPassword() {
        User user = userRepository.findById(3).orElseThrow();
        user.setPassword(passwordEncoder.encode(KNOWN_PASSWORD));
        userRepository.save(user);
    }

    private MockHttpServletRequestBuilder putAsUser3(String body) {
        return put("/users/me")
                .with(jwt().jwt(j -> j.subject("3")).authorities(new SimpleGrantedAuthority("ROLE_Employee")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Test
    void updateUsernameAndEmail_ok() throws Exception {
        mockMvc.perform(putAsUser3("{\"currentPassword\": \"" + KNOWN_PASSWORD + "\", \"newUsername\": \"employee02renamed\", \"newEmail\": \"renamed@ers.local\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("employee02renamed"))
                .andExpect(jsonPath("$.email").value("renamed@ers.local"))
                // hardening carried over: the hash never appears in JSON
                .andExpect(jsonPath("$.password").doesNotExist());

        assertEquals("employee02renamed", userRepository.findById(3).orElseThrow().getUsername());
    }

    @Test
    void changePassword_ok_andHashActuallyRotates() throws Exception {
        mockMvc.perform(putAsUser3("{\"currentPassword\": \"" + KNOWN_PASSWORD + "\", \"newPassword\": \"a-brand-new-secret\"}"))
                .andExpect(status().isOk());

        String storedHash = userRepository.findById(3).orElseThrow().getPassword();
        assertTrue(passwordEncoder.matches("a-brand-new-secret", storedHash));
    }

    @Test
    void wrongCurrentPassword_is403_andNothingChanges() throws Exception {
        String usernameBefore = userRepository.findById(3).orElseThrow().getUsername();

        mockMvc.perform(putAsUser3("{\"currentPassword\": \"not-it\", \"newUsername\": \"hacker\"}"))
                .andExpect(status().isForbidden());

        assertEquals(usernameBefore, userRepository.findById(3).orElseThrow().getUsername());
    }

    @Test
    void takenUsername_is409() throws Exception {
        // "admin" belongs to user 1 - clean semantics: explicit conflict, not the monolith's silent skip
        mockMvc.perform(putAsUser3("{\"currentPassword\": \"" + KNOWN_PASSWORD + "\", \"newUsername\": \"admin\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void takenEmail_is409() throws Exception {
        String someoneElsesEmail = userRepository.findById(1).orElseThrow().getEmail();
        mockMvc.perform(putAsUser3("{\"currentPassword\": \"" + KNOWN_PASSWORD + "\", \"newEmail\": \"" + someoneElsesEmail + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void resubmittingYourOwnUsername_isNotAConflict() throws Exception {
        // the uniqueness check must ignore the caller's own row
        mockMvc.perform(putAsUser3("{\"currentPassword\": \"" + KNOWN_PASSWORD + "\", \"newUsername\": \"employee02\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void nothingToUpdate_is400() throws Exception {
        mockMvc.perform(putAsUser3("{\"currentPassword\": \"" + KNOWN_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noToken_is401() throws Exception {
        mockMvc.perform(put("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"x\", \"newUsername\": \"y\"}"))
                .andExpect(status().isUnauthorized());
    }
}
