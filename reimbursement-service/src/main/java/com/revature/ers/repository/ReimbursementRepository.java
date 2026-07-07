package com.revature.ers.repository;

import com.revature.ers.model.Reimbursement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReimbursementRepository extends JpaRepository<Reimbursement, Integer> {

    /**
     * The reimbursement rows for a request, reached through their final approval (the table
     * has no requestid column). A list, not an Optional: the fan-out invariant says exactly
     * one per request, but the legacy seed carries a second row hung off a non-top approval
     * (request 2) - a unique lookup here is precisely how the monolith's approve-path would
     * crash with an uncaught NonUniqueResultException on that data.
     */
    List<Reimbursement> findByFinalApproval_Request_RequestId(int requestId);
}
