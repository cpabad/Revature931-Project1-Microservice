package com.revature.ers.controller;

import com.revature.ers.dto.ApprovalDecisionRequest;
import com.revature.ers.dto.ApprovalDecisionResponse;
import com.revature.ers.model.ApprovalOutcome;
import com.revature.ers.model.SupervisorApproval;
import com.revature.ers.service.ApprovalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The manager's side of the approval chain (Supervisor-only via SecurityConfig path rules).
 * The acting manager is always the token's subject - the monolith read the same fact from
 * the HttpSession; here nobody can decide on someone else's behalf by editing a parameter.
 *
 *   GET /approvals/pending           -> my still-pending votes
 *   PUT /requests/{id}/approval      -> apply my approve/deny; body {"approved": bool}
 */
@RestController
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/approvals/pending")
    public ResponseEntity<List<SupervisorApproval>> myPendingApprovals(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(approvalService.findPendingForSupervisor(Integer.parseInt(jwt.getSubject())));
    }

    @PutMapping("/requests/{requestId}/approval")
    public ResponseEntity<ApprovalDecisionResponse> decide(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable int requestId,
                                                           @RequestBody ApprovalDecisionRequest body) {
        return approvalService.resolveApproval(requestId, Integer.parseInt(jwt.getSubject()), body.approved())
                // WAITING_ON_OTHERS = the decision cannot be taken yet -> 409 Conflict
                // (the monolith said 400; 409 is the REST spelling of "valid request, wrong state")
                .map(outcome -> outcome == ApprovalOutcome.WAITING_ON_OTHERS
                        ? ResponseEntity.status(HttpStatus.CONFLICT).body(new ApprovalDecisionResponse(outcome))
                        : ResponseEntity.ok(new ApprovalDecisionResponse(outcome)))
                // no vote for this manager on this request
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
