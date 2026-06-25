package com.revature.ers.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tracer: GET /roles flows controller -> service -> Spring Data repo -> Hibernate
 * -> the seeded PostgreSQL, and the seeded rows come back as JSON. Proves the whole stack.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getAllRoles_returnsSeededRoles() throws Exception {
        // GET /roles is now Supervisor-only (via @PreAuthorize), so present that authority.
        mockMvc.perform(get("/roles").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Supervisor"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.role == 'Employee')]").exists())
                .andExpect(jsonPath("$[?(@.role == 'Supervisor')]").exists());
    }

    /** Proves the deny-by-default lockdown: no Bearer token -> 401, no controller reached. */
    @Test
    void getAllRoles_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/roles"))
                .andExpect(status().isUnauthorized());
    }
}
