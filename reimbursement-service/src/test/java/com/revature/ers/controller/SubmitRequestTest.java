package com.revature.ers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.revature.ers.model.Reimbursement;
import com.revature.ers.model.SupervisorApproval;
import com.revature.ers.repository.ReimbursementRepository;
import com.revature.ers.repository.SupervisorApprovalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /requests end-to-end: the submit fan-out ported from the monolith. @Transactional
 * rolls the inserted rows back (sequences advance, but nothing depends on their values).
 *
 * Seed fixture: user 3 (employee02) has two direct supervisors - user 1 (top-of-chain) and
 * user 2 (who reports to user 1). So a submit by user 3 must create two pending votes, and
 * exactly one reimbursement, hung off the TOP supervisor's vote.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubmitRequestTest {

    private static final int PENDING = 2;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SupervisorApprovalRepository supervisorApprovalRepository;
    @Autowired private ReimbursementRepository reimbursementRepository;

    @Test
    void submit_fansOutApprovalsAndReimbursement() throws Exception {
        MvcResult result = mockMvc.perform(post("/requests")
                        .with(jwt().jwt(j -> j.subject("3")).authorities(new SimpleGrantedAuthority("ROLE_Employee")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 42.5, \"eventDate\": \"2026-07-01\", \"eventLocationId\": 1, \"requestedEvent\": \"Team Offsite\"}"))
                .andExpect(status().isCreated())
                // save() returned the generated id - no insert-then-re-find hack
                .andExpect(jsonPath("$.requestId").isNumber())
                // requester came from the token, not the body
                .andExpect(jsonPath("$.requester.userId").value(3))
                .andExpect(jsonPath("$.status").value("Pending"))
                .andReturn();

        int requestId = objectMapper.readTree(result.getResponse().getContentAsString()).get("requestId").asInt();

        // one pending, undecided vote per direct supervisor of user 3 (users 1 and 2)
        List<SupervisorApproval> votes = supervisorApprovalRepository.findByRequest_RequestId(requestId);
        assertEquals(2, votes.size());
        for (SupervisorApproval vote : votes) {
            assertEquals(PENDING, vote.getStatus().getStatusId());
            assertFalse(vote.isApproval());
        }

        // exactly one reimbursement, hung off the top-of-chain supervisor's (user 1's) vote
        List<Reimbursement> reimbursements = reimbursementRepository.findByFinalApproval_Request_RequestId(requestId);
        assertEquals(1, reimbursements.size());
        Reimbursement reimbursement = reimbursements.get(0);
        assertEquals(1, reimbursement.getFinalApproval().getHierarchy().getSupervisor().getUserId());
        assertEquals(42.5, reimbursement.getAmount(), 0.001);
        assertEquals(PENDING, reimbursement.getStatus().getStatusId());
    }

    @Test
    void submitWithUnknownLocation_is400() throws Exception {
        mockMvc.perform(post("/requests")
                        .with(jwt().jwt(j -> j.subject("3")).authorities(new SimpleGrantedAuthority("ROLE_Employee")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 42.5, \"eventDate\": \"2026-07-01\", \"eventLocationId\": 9999, \"requestedEvent\": \"Nowhere\"}"))
                .andExpect(status().isBadRequest());
    }
}
