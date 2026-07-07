package com.revature.ers.controller;

import com.revature.ers.repository.ReimbursementRepository;
import com.revature.ers.repository.RequestRepository;
import com.revature.ers.repository.SupervisorApprovalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The approval chain against the seeded graph. @Transactional rolls every test back, so the
 * shared seed (which the monolith's integration tests also depend on) stays untouched.
 *
 * Seed fixtures used here:
 *   - user 1 (admin) is top-of-chain: no hierarchy row has them as employee.
 *   - user 2 (employee1, Supervisor role) reports to user 1.
 *   - request 1 (requester 2) has pending votes: approval 1 (supervisor 2), approval 2 (supervisor 1).
 *   - request 2 (requester 4) has approval 4 (supervisor 1, pending) + approval 3 (supervisor 2, already resolved);
 *     reimbursement 3 hangs off approval 4.
 *   - request 3 has no approval rows at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApprovalControllerTest {

    private static final int RESOLVED = 1;
    private static final int PENDING = 2;

    @Autowired private MockMvc mockMvc;
    @Autowired private SupervisorApprovalRepository supervisorApprovalRepository;
    @Autowired private RequestRepository requestRepository;
    @Autowired private ReimbursementRepository reimbursementRepository;

    @Test
    void approveAsTopOfChain_resolvesRequestAndReimbursement() throws Exception {
        mockMvc.perform(put("/requests/2/approval")
                        .with(jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_Supervisor")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPROVED"));

        // the manager's vote is recorded and resolved...
        assertTrue(supervisorApprovalRepository.findById(4).orElseThrow().isApproval());
        assertEquals(RESOLVED, supervisorApprovalRepository.findById(4).orElseThrow().getStatus().getStatusId());
        // ...and the whole chain settles: request + reimbursement resolved in the same transaction
        assertEquals(RESOLVED, requestRepository.findById(2).orElseThrow().getRequestStatus().getStatusId());
        assertEquals(RESOLVED, reimbursementRepository.findById(3).orElseThrow().getStatus().getStatusId());
        // pins the resolve-all deviation: the seed's stray second reimbursement for request 2
        // (hung off a non-top approval) resolves too, where the monolith's unique lookup would 500
        assertEquals(RESOLVED, reimbursementRepository.findById(2).orElseThrow().getStatus().getStatusId());
    }

    @Test
    void denyAsTopOfChain_resolvesAsDenied() throws Exception {
        mockMvc.perform(put("/requests/2/approval")
                        .with(jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_Supervisor")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DENIED"));

        assertFalse(supervisorApprovalRepository.findById(4).orElseThrow().isApproval());
        assertEquals(RESOLVED, requestRepository.findById(2).orElseThrow().getRequestStatus().getStatusId());
        assertEquals(RESOLVED, reimbursementRepository.findById(3).orElseThrow().getStatus().getStatusId());
    }

    @Test
    void approveBelowTopOfChain_escalates() throws Exception {
        // manager 2 approves request 1, but manager 2 reports to user 1 -> the request climbs
        mockMvc.perform(put("/requests/1/approval")
                        .with(jwt().jwt(j -> j.subject("2")).authorities(new SimpleGrantedAuthority("ROLE_Supervisor")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ESCALATED"));

        // only the vote persists; the request itself is still pending for supervisor 1
        assertTrue(supervisorApprovalRepository.findById(1).orElseThrow().isApproval());
        assertEquals(RESOLVED, supervisorApprovalRepository.findById(1).orElseThrow().getStatus().getStatusId());
        assertEquals(PENDING, requestRepository.findById(1).orElseThrow().getRequestStatus().getStatusId());
    }

    @Test
    void decideWithoutAVote_is404() throws Exception {
        // request 3 has no approval rows, so manager 1 has nothing to decide
        mockMvc.perform(put("/requests/3/approval")
                        .with(jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_Supervisor")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void employeeRole_is403() throws Exception {
        mockMvc.perform(put("/requests/1/approval")
                        .with(jwt().jwt(j -> j.subject("3")).authorities(new SimpleGrantedAuthority("ROLE_Employee")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void noToken_is401() throws Exception {
        mockMvc.perform(put("/requests/1/approval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pendingInbox_listsOnlyMyPendingVotes() throws Exception {
        // supervisor 1's pending votes in the seed: approval 2 (request 1) and approval 4 (request 2)
        mockMvc.perform(get("/approvals/pending")
                        .with(jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_Supervisor"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status.statusId").value(PENDING))
                .andExpect(jsonPath("$[1].status.statusId").value(PENDING));
    }
}
