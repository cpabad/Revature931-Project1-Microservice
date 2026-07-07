package com.revature.ers.dto;

import java.time.LocalDate;

/**
 * Inbound POST /requests body. The requester is NOT a field - it comes from the JWT subject,
 * so a user can only ever submit for themselves (the monolith read the same fact from the
 * session). Locations are reference data, addressed by id.
 */
public record SubmitRequestDto(double amount, LocalDate eventDate, int eventLocationId, String requestedEvent) {
}
