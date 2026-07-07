package com.revature.ers.messaging;

import com.revature.ers.dto.SubmitRequestDto;
import com.revature.ers.event.RequestSubmissionEvent;
import com.revature.ers.model.Request;
import com.revature.ers.service.RequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The asynchronous intake: consumes submission events (published by ers-soap-adapter) and runs
 * them through the SAME RequestService.submit fan-out the REST endpoint uses - one domain rule,
 * two transports.
 *
 * Semantics are at-least-once: a crash after submit() but before the offset commit replays the
 * event, and a bad event would otherwise be retried forever. The catch below therefore logs and
 * DROPS failures - the training-wheels version of a dead-letter topic, which is what production
 * would use (along with an idempotency key on correlationId to de-duplicate replays).
 */
@Component
public class RequestSubmissionListener {

    private static final Logger LOG = LoggerFactory.getLogger(RequestSubmissionListener.class);

    private final RequestService requestService;

    public RequestSubmissionListener(RequestService requestService) {
        this.requestService = requestService;
    }

    @KafkaListener(topics = "${ers.kafka.topic.request-submitted}", groupId = "ers-reimbursement")
    public void onSubmission(RequestSubmissionEvent event) {
        try {
            Optional<Request> created = requestService.submit(
                    event.requesterUserId(),
                    new SubmitRequestDto(event.amount(), event.eventDate(), event.eventLocationId(), event.requestedEvent()));
            if (created.isPresent()) {
                LOG.info("submission {} -> request {} (requester {})",
                        event.correlationId(), created.get().getRequestId(), event.requesterUserId());
            } else {
                LOG.warn("submission {} dropped: unknown event location {} or requester {}",
                        event.correlationId(), event.eventLocationId(), event.requesterUserId());
            }
        } catch (Exception e) {
            LOG.error("submission {} dropped: processing failed", event.correlationId(), e);
        }
    }
}
