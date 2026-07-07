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

/**
 * The one SOAP operation: a legacy partner submits a reimbursement request. The endpoint
 * validates the payload's business basics, publishes a JSON event, and acknowledges with a
 * correlation id - "accepted" means QUEUED, not persisted; processing is asynchronous in
 * reimbursement-service's Kafka listener.
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
        // key = requester id: submissions from the same user stay ordered on one partition
        kafkaTemplate.send(topic, String.valueOf(request.getRequesterUserId()), event);
        LOG.info("queued SOAP submission {} for requester {}", correlationId, request.getRequesterUserId());

        response.setAccepted(true);
        response.setCorrelationId(correlationId);
        response.setMessage("Submission queued for processing");
        return response;
    }
}
