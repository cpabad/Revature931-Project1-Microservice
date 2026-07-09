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
 * End-to-end tracer for the Request slice: controller -> service -> Spring Data repo ->
 * Hibernate (eager graph) -> seeded PostgreSQL -> JSON.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getAll_serializesGraphAndHidesPassword() throws Exception {
        // GET /requests is now Supervisor-only, so this data-shape check presents that authority.
        mockMvc.perform(get("/requests").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Supervisor"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").exists())
                // nested entity comes back through the graph...
                .andExpect(jsonPath("$[0].requestStatus.status").exists())
                // ...but the requester's password never does (@JsonIgnore carried over)
                .andExpect(jsonPath("$[0].requester.password").doesNotExist());
    }

    // Both routes now check ownership against the token subject, and the default jwt()
    // subject is the non-numeric "user" - so these present the owner's real userId (2).

    @Test
    void getById_returnsSeededRequestOne() throws Exception {
        mockMvc.perform(get("/requests/1").with(jwt().jwt(j -> j.subject("2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(1))
                .andExpect(jsonPath("$.requestedEvent").value("Anime Convention"))
                .andExpect(jsonPath("$.requester.userId").value(2))
                .andExpect(jsonPath("$.requestStatus.statusId").value(2));
    }

    @Test
    void getByRequester_filtersByUser() throws Exception {
        mockMvc.perform(get("/requests/requester/2").with(jwt().jwt(j -> j.subject("2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requester.userId").value(2));
    }
}
