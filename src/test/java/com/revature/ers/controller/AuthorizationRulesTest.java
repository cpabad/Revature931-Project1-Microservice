package com.revature.ers.controller;

import com.revature.ers.model.Role;
import com.revature.ers.model.User;
import com.revature.ers.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The role rules end-to-end, with REAL minted tokens (not the .with(jwt()) mock). Because the
 * token only carries a "role" claim string, a pass here also proves the JwtAuthenticationConverter
 * maps that claim to ROLE_Supervisor/ROLE_Employee - the linchpin the path rule and @PreAuthorize
 * both rely on. The three monolith filters' Supervisor/Employee split now lives entirely here.
 *
 * Status codes worth distinguishing:
 *   401 Unauthorized = "who are you?"  (no/invalid token - stopped in the filter chain)
 *   403 Forbidden    = "I know who you are, you're not allowed" (valid token, wrong role)
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    private String supervisorToken() {
        User u = new User();
        u.setUserId(1);
        u.setRole(new Role(1, "Supervisor"));
        return tokenService.mint(u);
    }

    private String employeeToken() {
        User u = new User();
        u.setUserId(2);
        u.setRole(new Role(2, "Employee"));
        return tokenService.mint(u);
    }

    // --- Mechanism #1: path rule on GET /requests (Supervisor-only) ---------------------------

    @Test
    void supervisor_canListAllRequests() throws Exception {
        mockMvc.perform(get("/requests").header("Authorization", "Bearer " + supervisorToken()))
                .andExpect(status().isOk());
    }

    @Test
    void employee_cannotListAllRequests_isForbidden() throws Exception {
        mockMvc.perform(get("/requests").header("Authorization", "Bearer " + employeeToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void noToken_onSupervisorRoute_isUnauthorized() throws Exception {
        mockMvc.perform(get("/requests"))
                .andExpect(status().isUnauthorized());
    }

    // --- Mechanism #2: @PreAuthorize on GET /roles (Supervisor-only) ---------------------------

    @Test
    void supervisor_canListRoles() throws Exception {
        mockMvc.perform(get("/roles").header("Authorization", "Bearer " + supervisorToken()))
                .andExpect(status().isOk());
    }

    @Test
    void employee_cannotListRoles_isForbidden() throws Exception {
        mockMvc.perform(get("/roles").header("Authorization", "Bearer " + employeeToken()))
                .andExpect(status().isForbidden());
    }

    // --- Shared route: any authenticated role may view requests by requester -------------------

    @Test
    void employee_canViewRequestsByRequester() throws Exception {
        mockMvc.perform(get("/requests/requester/2").header("Authorization", "Bearer " + employeeToken()))
                .andExpect(status().isOk());
    }
}
