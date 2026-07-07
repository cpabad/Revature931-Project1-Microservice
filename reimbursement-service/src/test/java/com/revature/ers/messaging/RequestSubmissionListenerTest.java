package com.revature.ers.messaging;

import com.revature.ers.model.Request;
import com.revature.ers.repository.RequestRepository;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Raw JSON on the topic -> a persisted request, against a real (in-JVM) broker and the real
 * database. The producer here is a plain StringSerializer sending a JSON literal - deliberately
 * NOT this service's event class - so the test proves the listener deserializes by field names
 * alone, exactly what it must do with events from ers-soap-adapter (whose test pins the same
 * literal from the producing side).
 *
 * NOT @Transactional: the listener runs on the container thread in its own transaction, so this
 * test cleans up its row explicitly. Requester 5 (employee4) has no supervisors in the seed, so
 * the fan-out creates no approval/reimbursement rows - one request row in, one deleted.
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = "reimbursement.request.submitted")
class RequestSubmissionListenerTest {

    private static final String TOPIC = "reimbursement.request.submitted";
    private static final String MARKER = "Kafka Intake Contract Test";

    @Autowired private EmbeddedKafkaBroker embeddedKafka;
    @Autowired private RequestRepository requestRepository;

    @AfterEach
    void cleanUp() {
        requestRepository.findByRequester_UserId(5).stream()
                .filter(r -> MARKER.equals(r.getRequestedEvent()))
                .forEach(requestRepository::delete);
    }

    @Test
    void submissionEvent_becomesAPersistedPendingRequest() {
        String eventJson = """
                {"correlationId":"test-correlation-1","requesterUserId":5,"amount":77.75,\
                "eventDate":"2026-07-01","eventLocationId":1,"requestedEvent":"%s"}""".formatted(MARKER);

        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafka);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(TOPIC, "5", eventJson));
            producer.flush();
        }

        // ignoreExceptions: untilAsserted only retries AssertionErrors by default, and the
        // orElseThrow below throws NoSuchElementException until the listener has consumed
        await().atMost(Duration.ofSeconds(20)).ignoreExceptions().untilAsserted(() -> {
            Request created = requestRepository.findByRequester_UserId(5).stream()
                    .filter(r -> MARKER.equals(r.getRequestedEvent()))
                    .findFirst().orElseThrow();
            assertEquals(77.75, created.getAmount(), 0.001);
            assertEquals(2, created.getRequestStatus().getStatusId());   // pending
            assertEquals(1, created.getEventLocation().getLocationId());
        });
    }
}
