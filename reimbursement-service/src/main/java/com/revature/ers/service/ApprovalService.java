package com.revature.ers.service;

import com.revature.ers.model.ApprovalOutcome;
import com.revature.ers.model.Hierarchy;
import com.revature.ers.model.Reimbursement;
import com.revature.ers.model.Request;
import com.revature.ers.model.SupervisorApproval;
import com.revature.ers.model.User;
import com.revature.ers.repository.HierarchyRepository;
import com.revature.ers.repository.ReimbursementRepository;
import com.revature.ers.repository.ReimbursementStatusRepository;
import com.revature.ers.repository.RequestRepository;
import com.revature.ers.repository.RequestStatusRepository;
import com.revature.ers.repository.SupervisorApprovalRepository;
import com.revature.ers.repository.SupervisorApprovalStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * The approval-chain rules, ported 1:1 from the monolith's
 * SupervisorApprovalService.resolveApproval (itself extracted from RequestHelper in the
 * 2026-07 stabilization pass). Two upgrades the port buys for free:
 *
 *  - @Transactional: the monolith wrote the approval, request, and reimbursement in three
 *    separate transactions - a crash in between left the decision half-applied. Here the
 *    whole resolution commits or rolls back as one unit.
 *  - Optional instead of NPE: an unknown request/manager combination becomes a 404 upstream,
 *    where the monolith would have thrown on a null approval.
 *
 * The counters are computed from database state BEFORE the decision is applied, exactly as
 * the monolith did (it mutated a detached copy and re-read fresh rows); mutation only happens
 * on the paths that persist, so the WAITING path writes nothing - JPA dirty-checking would
 * otherwise flush a mutated managed entity at commit even without an explicit save.
 */
@Service
public class ApprovalService {

    private static final int RESOLVED = 1;
    private static final int PENDING = 2;

    private final SupervisorApprovalRepository supervisorApprovalRepository;
    private final SupervisorApprovalStatusRepository supervisorApprovalStatusRepository;
    private final HierarchyRepository hierarchyRepository;
    private final RequestRepository requestRepository;
    private final RequestStatusRepository requestStatusRepository;
    private final ReimbursementRepository reimbursementRepository;
    private final ReimbursementStatusRepository reimbursementStatusRepository;

    public ApprovalService(SupervisorApprovalRepository supervisorApprovalRepository,
                           SupervisorApprovalStatusRepository supervisorApprovalStatusRepository,
                           HierarchyRepository hierarchyRepository,
                           RequestRepository requestRepository,
                           RequestStatusRepository requestStatusRepository,
                           ReimbursementRepository reimbursementRepository,
                           ReimbursementStatusRepository reimbursementStatusRepository) {
        this.supervisorApprovalRepository = supervisorApprovalRepository;
        this.supervisorApprovalStatusRepository = supervisorApprovalStatusRepository;
        this.hierarchyRepository = hierarchyRepository;
        this.requestRepository = requestRepository;
        this.requestStatusRepository = requestStatusRepository;
        this.reimbursementRepository = reimbursementRepository;
        this.reimbursementStatusRepository = reimbursementStatusRepository;
    }

    /** A manager's inbox: their still-pending votes across all requests. */
    public List<SupervisorApproval> findPendingForSupervisor(int supervisorUserId) {
        return supervisorApprovalRepository.findByHierarchy_Supervisor_UserIdAndStatus_StatusId(supervisorUserId, PENDING);
    }

    /**
     * Applies a manager's approve/deny to their vote on a request and works out where the
     * request now stands. Empty = this manager has no vote on this request (-> 404).
     */
    @Transactional
    public Optional<ApprovalOutcome> resolveApproval(int requestId, int managerId, boolean decision) {
        Optional<SupervisorApproval> found =
                supervisorApprovalRepository.findByRequest_RequestIdAndHierarchy_Supervisor_UserId(requestId, managerId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        SupervisorApproval approval = found.get();

        List<User> managersEmployees = hierarchyRepository.findBySupervisor_UserId(managerId)
                .stream().map(Hierarchy::getEmployee).toList();
        int pendingNonTopApprovals = 0;
        int pendingSubordinateApprovals = 0;
        for (SupervisorApproval a : supervisorApprovalRepository.findByRequest_RequestId(requestId)) {
            boolean stillPending = a.getStatus().getStatusId() == PENDING;
            User votingSupervisor = a.getHierarchy().getSupervisor();
            if (hierarchyRepository.existsByEmployee_UserId(votingSupervisor.getUserId()) && stillPending) {
                pendingNonTopApprovals++;
            } else if (managersEmployees.contains(votingSupervisor) && stillPending) {
                pendingSubordinateApprovals++;
            }
        }

        if (pendingNonTopApprovals == 0 && !hierarchyRepository.existsByEmployee_UserId(managerId) && decision) {
            applyDecision(approval, decision);
            resolveRequestAndReimbursement(approval.getRequest());
            return Optional.of(ApprovalOutcome.APPROVED);
        } else if (pendingSubordinateApprovals == 0 && decision) {
            applyDecision(approval, decision);
            return Optional.of(ApprovalOutcome.ESCALATED);
        } else if (pendingSubordinateApprovals == 0) {
            applyDecision(approval, decision);
            resolveRequestAndReimbursement(approval.getRequest());
            return Optional.of(ApprovalOutcome.DENIED);
        } else {
            return Optional.of(ApprovalOutcome.WAITING_ON_OTHERS);
        }
    }

    private void applyDecision(SupervisorApproval approval, boolean decision) {
        approval.setApproval(decision);
        approval.setStatus(supervisorApprovalStatusRepository.findById(RESOLVED).orElseThrow());
        supervisorApprovalRepository.save(approval);
    }

    private void resolveRequestAndReimbursement(Request request) {
        request.setRequestStatus(requestStatusRepository.findById(RESOLVED).orElseThrow());
        requestRepository.save(request);
        // Normally exactly one row (the fan-out invariant); resolving all of them keeps the
        // decision total on legacy data where a stray extra row exists - the monolith's
        // getSingleResult would 500 there instead.
        List<Reimbursement> reimbursements = reimbursementRepository
                .findByFinalApproval_Request_RequestId(request.getRequestId());
        if (reimbursements.isEmpty()) {
            throw new IllegalStateException(
                    "No reimbursement row for request " + request.getRequestId() + " - the submit fan-out should have created it");
        }
        for (Reimbursement reimbursement : reimbursements) {
            reimbursement.setStatus(reimbursementStatusRepository.findById(RESOLVED).orElseThrow());
            reimbursementRepository.save(reimbursement);
        }
    }
}
