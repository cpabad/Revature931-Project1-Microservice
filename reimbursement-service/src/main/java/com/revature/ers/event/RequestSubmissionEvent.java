package com.revature.ers.event;

import java.time.LocalDate;

/**
 * This service's OWN copy of the wire contract on {@code reimbursement.request.submitted}
 * (the producer, ers-soap-adapter, has its own identical record - same JSON field names, no
 * shared library, duplication over coupling). Deserialized by field name from the JSON body;
 * producer type headers are ignored (see application.properties).
 */
public record RequestSubmissionEvent(
        String correlationId,
        int requesterUserId,
        double amount,
        LocalDate eventDate,
        int eventLocationId,
        String requestedEvent) {
}
