package com.revature.ers.soap.endpoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

/**
 * SOAP in -> JSON event out, against a REAL (in-JVM) Kafka broker. The Kafka assertion reads
 * the RAW string from the topic and checks the JSON field names literally - that pins the wire
 * contract from the producer side (the consumer's test in reimbursement-service pins the same
 * JSON from its side; the two string literals ARE the cross-service contract test).
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = "reimbursement.request.submitted")
class RequestSubmissionEndpointTest {

    private static final String TOPIC = "reimbursement.request.submitted";
    private static final Map<String, String> NS = Map.of("ns", "http://revature.com/ers/soap/requests");

    @Autowired private ApplicationContext applicationContext;
    @Autowired private EmbeddedKafkaBroker embeddedKafka;

    private MockWebServiceClient mockClient;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockClient = MockWebServiceClient.createClient(applicationContext);
    }

    @Test
    void soapSubmission_isAcknowledgedAndPublishedAsJsonEvent() throws Exception {
        StringSource payload = new StringSource("""
                <submitReimbursementRequest xmlns="http://revature.com/ers/soap/requests">
                    <requesterUserId>3</requesterUserId>
                    <amount>88.25</amount>
                    <eventDate>2026-07-01</eventDate>
                    <eventLocationId>1</eventLocationId>
                    <requestedEvent>SOAP Partner Conference</requestedEvent>
                </submitReimbursementRequest>""");

        // note: evaluatesTo(String) compares the element's TEXT; evaluatesTo(boolean) would
        // XPath-coerce the node-set ("does the element exist") and always be true here
        mockClient.sendRequest(withPayload(payload))
                .andExpect(noFault())
                .andExpect(xpath("/ns:submitReimbursementResponse/ns:accepted", NS).evaluatesTo("true"));

        // read the raw event back off the broker and assert the JSON contract literally
        Map<String, Object> props = KafkaTestUtils.consumerProps("contract-check", "true", embeddedKafka);
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(consumer, TOPIC);
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(10));

            // key = requester id (per-user ordering)
            assertEquals("3", record.key());
            JsonNode event = json.readTree(record.value());
            assertEquals(3, event.get("requesterUserId").asInt());
            assertEquals(88.25, event.get("amount").asDouble(), 0.001);
            assertEquals("2026-07-01", event.get("eventDate").asText());
            assertEquals(1, event.get("eventLocationId").asInt());
            assertEquals("SOAP Partner Conference", event.get("requestedEvent").asText());
            assertFalse(event.get("correlationId").asText().isBlank());
        }
    }

    @Test
    void nonPositiveAmount_isRejectedWithoutPublishing() {
        StringSource payload = new StringSource("""
                <submitReimbursementRequest xmlns="http://revature.com/ers/soap/requests">
                    <requesterUserId>3</requesterUserId>
                    <amount>-5.00</amount>
                    <eventDate>2026-07-01</eventDate>
                    <eventLocationId>1</eventLocationId>
                    <requestedEvent>Nice Try</requestedEvent>
                </submitReimbursementRequest>""");

        mockClient.sendRequest(withPayload(payload))
                .andExpect(noFault())
                .andExpect(xpath("/ns:submitReimbursementResponse/ns:accepted", NS).evaluatesTo("false"));
    }
}
