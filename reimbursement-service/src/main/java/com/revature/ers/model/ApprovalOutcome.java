package com.revature.ers.model;

/**
 * Where a request stands after a manager's decision (ported 1:1 from the monolith's
 * ApprovalOutcome).
 */
public enum ApprovalOutcome {
    /** Final approval reached: request and reimbursement resolved as approved. */
    APPROVED,
    /** Denial is final: request and reimbursement resolved as denied. */
    DENIED,
    /** This manager approved, but a supervisor above still has to decide. */
    ESCALATED,
    /**
     * Peer managers below still have pending decisions; nothing persisted. Kept for parity
     * with the monolith, but unreachable with referentially consistent data: a "subordinate"
     * supervisor by definition has a hierarchy row above them, which routes their pending
     * approval into the non-top counter instead.
     */
    WAITING_ON_OTHERS
}
