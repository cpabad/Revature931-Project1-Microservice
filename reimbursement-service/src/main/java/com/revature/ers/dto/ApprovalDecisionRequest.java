package com.revature.ers.dto;

/** Inbound PUT /requests/{id}/approval body: the manager's verdict. */
public record ApprovalDecisionRequest(boolean approved) {
}
