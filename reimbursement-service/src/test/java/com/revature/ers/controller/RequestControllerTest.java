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
 * Hibernate (LAZY associations fetched per-query by @EntityGraph) -> seeded PostgreSQL ->
 * RequestResponse DTO -> JSON.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getAll_serializesGraphAndHidesPassword() throws Exception {
        // GET /requests is now Supervisor-only AND paged, so rows live under $.content and the
        // data-shape check presents the Supervisor authority.
        mockMvc.perform(get("/requests").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Supervisor"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].requestId").exists())
                // the DTO carries the status STRING, not the entity's status object
                .andExpect(jsonPath("$.content[0].status").exists())
                // and the requester summary structurally cannot carry a password
                .andExpect(jsonPath("$.content[0].requester.password").doesNotExist());
    }

    @Test
    void getAll_capsPageSizeAtHundred() throws Exception {
        // ?size=100000 must NOT return an unbounded page - the max-page-size cap clamps it to
        // 100, proving the OOM guard cannot be bypassed from the query string.
        mockMvc.perform(get("/requests?size=100000")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Supervisor"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageable.pageSize").value(100));
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
                .andExpect(jsonPath("$.status").value("Pending"))
                // the DTO flattens the location's postal join - city/state inside the address
                .andExpect(jsonPath("$.eventLocation.city").exists());
    }

    @Test
    void getByRequester_filtersByUser() throws Exception {
        mockMvc.perform(get("/requests/requester/2").with(jwt().jwt(j -> j.subject("2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requester.userId").value(2));
    }
}
