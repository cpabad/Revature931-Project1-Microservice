package com.revature.ers.dto;

import com.revature.ers.model.SupervisorApproval;

/**
 * One inbox item: the vote's id (what PUT /requests/{id}/approval needs) plus the request
 * being voted on. The entity version serialized approval -> request -> requester/location/
 * status -> hierarchy -> two more users - the deepest graph in the API, all of it eager.
 * An inbox item's status is "Pending" by definition (that IS the inbox query), so it does
 * not appear here; the decided/undecided sentinel date was schema plumbing, not information.
 */
public record PendingApprovalResponse(int approvalId, RequestResponse request) {

    public static PendingApprovalResponse from(SupervisorApproval approval) {
        return new PendingApprovalResponse(approval.getApprovalId(), RequestResponse.from(approval.getRequest()));
    }
}
