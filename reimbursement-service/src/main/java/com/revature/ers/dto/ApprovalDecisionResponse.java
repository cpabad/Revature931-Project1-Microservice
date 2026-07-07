package com.revature.ers.dto;

import com.revature.ers.model.ApprovalOutcome;

/** Outbound PUT /requests/{id}/approval body: what the decision settled. */
public record ApprovalDecisionResponse(ApprovalOutcome outcome) {
}
