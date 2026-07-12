package com.revature.ers.messaging;

import com.revature.ers.dto.SubmitRequestDto;
import com.revature.ers.event.RequestSubmissionEvent;
import com.revature.ers.model.ProcessedEvent;
import com.revature.ers.model.Request;
import com.revature.ers.repository.ProcessedEventRepository;
import com.revature.ers.service.RequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The asynchronous intake: consumes submission events (published by ers-soap-adapter) and runs
 * them through the SAME RequestService.submit fan-out the REST endpoint uses - one domain rule,
 * two transports.
 *
 * Delivery is at-least-once, and the two failure modes are handled separately:
 *
 * DUPLICATES (idempotent consumer): a crash between the database commit and the offset commit
 * replays the event. The listener records each correlation id in {@code processed_event} in the
 * SAME transaction as the domain write, so "request row exists" and "id marked processed" are
 * atomic - a redelivered id is recognized and skipped. A deterministic business rejection
 * (unknown location/requester) is ALSO marked processed: replaying it can never succeed, so its
 * outcome is final too (the async twin of REST answering 4xx, not 5xx).
 *
 * FAILURES (dead-letter topic): exceptions are NOT caught here - they propagate to the
 * container's {@code DefaultErrorHandler} (see KafkaConfig), which retries with backoff for
 * transient faults and then publishes the record to {@code <topic>.DLT} instead of letting one
 * poison event block the partition forever. The rolled-back transaction includes the
 * processed_event marker, so a retry of a transient failure is NOT mistaken for a duplicate.
 */
@Component
public class RequestSubmissionListener {

    private static final Logger LOG = LoggerFactory.getLogger(RequestSubmissionListener.class);

    private final RequestService requestService;
    private final ProcessedEventRepository processedEvents;

    public RequestSubmissionListener(RequestService requestService, ProcessedEventRepository processedEvents) {
        this.requestService = requestService;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(topics = "${ers.kafka.topic.request-submitted}", groupId = "ers-reimbursement")
    @Transactional
    public void onSubmission(RequestSubmissionEvent event) {
        if (processedEvents.existsById(event.correlationId())) {
            LOG.info("submission {} skipped: already processed (at-least-once redelivery)", event.correlationId());
            return;
        }
        Optional<Request> created = requestService.submit(
                event.requesterUserId(),
                new SubmitRequestDto(event.amount(), event.eventDate(), event.eventLocationId(), event.requestedEvent()));
        if (created.isPresent()) {
            LOG.info("submission {} -> request {} (requester {})",
                    event.correlationId(), created.get().getRequestId(), event.requesterUserId());
        } else {
            LOG.warn("submission {} rejected: unknown event location {} or requester {} - marked processed (deterministic, replay cannot succeed)",
                    event.correlationId(), event.eventLocationId(), event.requesterUserId());
        }
        processedEvents.save(new ProcessedEvent(event.correlationId()));
    }
}
