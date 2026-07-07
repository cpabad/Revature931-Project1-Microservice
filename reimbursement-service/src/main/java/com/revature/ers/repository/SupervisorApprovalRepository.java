package com.revature.ers.repository;

import com.revature.ers.model.SupervisorApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupervisorApprovalRepository extends JpaRepository<SupervisorApproval, Integer> {

    /** All votes on one request (the monolith's findByRequestAndRequester; the requester was redundant - a request has one requester). */
    List<SupervisorApproval> findByRequest_RequestId(int requestId);

    /** This manager's own vote on this request (the monolith's findByRequestRequesterManager). */
    Optional<SupervisorApproval> findByRequest_RequestIdAndHierarchy_Supervisor_UserId(int requestId, int supervisorUserId);

    /**
     * A manager's inbox. The monolith loaded findAll() and filtered in Java
     * (findPendingRequestsForManager); this pushes both predicates into SQL.
     */
    List<SupervisorApproval> findByHierarchy_Supervisor_UserIdAndStatus_StatusId(int supervisorUserId, int statusId);
}
