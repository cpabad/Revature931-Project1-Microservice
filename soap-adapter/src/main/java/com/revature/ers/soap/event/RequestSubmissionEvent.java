package com.revature.ers.soap.event;

/**
 * The wire contract on the {@code reimbursement.request.submitted} topic (JSON). The consumer
 * (reimbursement-service) owns its own copy of this record - same field names, no shared
 * library - so the JSON field names ARE the contract, pinned textually by both sides' tests.
 * The correlationId is the adapter's tracing handle: it is returned to the SOAP caller and
 * travels with the event.
 *
 * eventDate is a String ON PURPOSE: it pins the wire format to ISO-8601 ("2026-07-01") in the
 * contract itself. (As a LocalDate, the default JsonSerializer mapper wrote it as [2026,7,1] -
 * the producing side's test caught the drift.)
 */
public record RequestSubmissionEvent(
        String correlationId,
        int requesterUserId,
        double amount,
        String eventDate,
        int eventLocationId,
        String requestedEvent) {
}
