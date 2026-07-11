package com.revature.ers.soap.endpoint;

import com.revature.ers.soap.event.RequestSubmissionEvent;
import com.revature.ers.soap.wsdl.SubmitReimbursementRequest;
import com.revature.ers.soap.wsdl.SubmitReimbursementResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * The one SOAP operation: a legacy partner submits a reimbursement request. The endpoint
 * validates the payload's business basics, publishes a JSON event, and acknowledges with a
 * correlation id - "accepted" means QUEUED (broker-acknowledged, acks=all), not persisted;
 * processing is asynchronous in reimbursement-service's Kafka listener.
 *
 * The ack is HONEST: the endpoint blocks on the send future before answering. Fire-and-forget
 * would return accepted=true + a correlation id for an event that was never published when the
 * broker is down - silent data loss wearing a receipt. The partner's call is synchronous
 * anyway, so the blocking wait costs nothing extra on the happy path (linger.ms=0) and is
 * bounded by the producer timeouts in application.properties when the broker is unreachable.
 *
 * TRUST BOUNDARY (deliberate, documented): a REST caller proves the requester's identity via
 * JWT; a SOAP partner ASSERTS requesterUserId in the payload. Production would authenticate
 * the partner itself (mTLS or WS-Security) and hold an allowlist of which user ids each
 * partner may submit for. Out of scope for this exercise - the adapter is not internet-facing.
 */
@Endpoint
public class RequestSubmissionEndpoint {

    private static final String NAMESPACE = "http://revature.com/ers/soap/requests";
    private static final Logger LOG = LoggerFactory.getLogger(RequestSubmissionEndpoint.class);

    private final KafkaTemplate<String, RequestSubmissionEvent> kafkaTemplate;
    private final String topic;

    public RequestSubmissionEndpoint(KafkaTemplate<String, RequestSubmissionEvent> kafkaTemplate,
                                     @Value("${ers.kafka.topic.request-submitted}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "submitReimbursementRequest")
    @ResponsePayload
    public SubmitReimbursementResponse submit(@RequestPayload SubmitReimbursementRequest request) {
        SubmitReimbursementResponse response = new SubmitReimbursementResponse();

        if (request.getAmount() <= 0) {
            response.setAccepted(false);
            response.setCorrelationId("");
            response.setMessage("amount must be positive");
            return response;
        }

        String correlationId = UUID.randomUUID().toString();
        // JAXB binds xs:date to XMLGregorianCalendar (a pre-java.time relic); convert once here
        // so everything past the SOAP boundary speaks java.time.
        LocalDate eventDate = LocalDate.of(
                request.getEventDate().getYear(),
                request.getEventDate().getMonth(),
                request.getEventDate().getDay());

        RequestSubmissionEvent event = new RequestSubmissionEvent(
                correlationId,
                request.getRequesterUserId(),
                request.getAmount(),
                eventDate.toString(),   // ISO-8601 on the wire, per the event contract
                request.getEventLocationId(),
                request.getRequestedEvent());
        try {
            // key = requester id: submissions from the same user stay ordered on one partition.
            // get() surfaces broker failure here instead of in a log nobody is watching; the
            // 15s ceiling is a backstop above delivery.timeout.ms (10s), which fails first.
            kafkaTemplate.send(topic, String.valueOf(request.getRequesterUserId()), event)
                    .get(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return rejected(response, correlationId, e);
        } catch (Exception e) {
            return rejected(response, correlationId, e);
        }
        LOG.info("queued SOAP submission {} for requester {}", correlationId, request.getRequesterUserId());

        response.setAccepted(true);
        response.setCorrelationId(correlationId);
        response.setMessage("Submission queued for processing");
        return response;
    }

    /** Nothing was published, so no correlation id goes out - there is nothing to correlate. */
    private static SubmitReimbursementResponse rejected(SubmitReimbursementResponse response,
                                                        String correlationId, Exception cause) {
        LOG.error("SOAP submission {} could not be queued", correlationId, cause);
        response.setAccepted(false);
        response.setCorrelationId("");
        response.setMessage("Submission could not be queued; please retry later");
        return response;
    }
}
