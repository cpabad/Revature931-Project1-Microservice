package com.revature.ers.soap.endpoint;

import com.revature.ers.soap.event.RequestSubmissionEvent;
import com.revature.ers.soap.wsdl.SubmitReimbursementRequest;
import com.revature.ers.soap.wsdl.SubmitReimbursementResponse;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The broker-down path, as a plain unit test: the integration test can only exercise the happy
 * path (its embedded broker is up by definition), so the honest-nack behavior is pinned here
 * with a mocked KafkaTemplate handing back a failed future - exactly what the producer returns
 * once delivery.timeout.ms expires against an unreachable broker.
 */
class RequestSubmissionEndpointFailureTest {

    @Test
    @SuppressWarnings("unchecked")
    void brokerFailure_isAnHonestNack_withNoCorrelationId() throws Exception {
        KafkaTemplate<String, RequestSubmissionEvent> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), any(RequestSubmissionEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker unreachable")));
        RequestSubmissionEndpoint endpoint =
                new RequestSubmissionEndpoint(kafka, "reimbursement.request.submitted");

        SubmitReimbursementResponse response = endpoint.submit(validRequest());

        assertFalse(response.isAccepted(), "an unpublished event must not be acknowledged");
        // no event was published, so no correlation id goes out - a nonzero id here would be
        // a receipt for nothing, the exact lie this fix removes
        assertTrue(response.getCorrelationId().isEmpty());
        assertFalse(response.getMessage().isBlank());
    }

    private static SubmitReimbursementRequest validRequest() throws Exception {
        SubmitReimbursementRequest request = new SubmitReimbursementRequest();
        request.setRequesterUserId(3);
        request.setAmount(88.25);
        request.setEventDate(DatatypeFactory.newInstance()
                .newXMLGregorianCalendarDate(2026, 7, 1, DatatypeConstants.FIELD_UNDEFINED));
        request.setEventLocationId(1);
        request.setRequestedEvent("SOAP Partner Conference");
        return request;
    }
}
