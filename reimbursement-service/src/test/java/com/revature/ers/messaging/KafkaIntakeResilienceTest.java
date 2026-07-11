package com.revature.ers.messaging;

import com.revature.ers.repository.ProcessedEventRepository;
import com.revature.ers.repository.RequestRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.awaitility.Awaitility.await;

/**
 * The at-least-once failure modes, end to end against the embedded broker and the real
 * database: duplicates are recognized (idempotent consumer), deterministic rejections are
 * final, and failures dead-letter instead of blocking the partition.
 *
 * The single partition (below) is load-bearing for the duplicate test: one partition means
 * total order, so once a SENTINEL event sent after the duplicates has been processed, both
 * duplicate deliveries are guaranteed to have been consumed - no sleep-and-hope.
 *
 * Requester 5 (employee4) has no supervisors in the seed, so submissions create no
 * approval/reimbursement rows - cleanup is the request row and the idempotency marker.
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"reimbursement.request.submitted", "reimbursement.request.submitted.DLT"})
class KafkaIntakeResilienceTest {

    private static final String TOPIC = "reimbursement.request.submitted";
    private static final String DLT = "reimbursement.request.submitted.DLT";

    @Autowired private EmbeddedKafkaBroker embeddedKafka;
    @Autowired private RequestRepository requestRepository;
    @Autowired private ProcessedEventRepository processedEventRepository;

    private final List<String> correlationIdsUsed = new java.util.ArrayList<>();
    private final List<String> markersUsed = new java.util.ArrayList<>();

    @AfterEach
    void cleanUp() {
        requestRepository.findByRequester_UserId(5).stream()
                .filter(r -> markersUsed.contains(r.getRequestedEvent()))
                .forEach(requestRepository::delete);
        correlationIdsUsed.forEach(id -> {
            if (processedEventRepository.existsById(id)) {
                processedEventRepository.deleteById(id);
            }
        });
    }

    private String eventJson(String correlationId, double amount, int locationId, String marker) {
        correlationIdsUsed.add(correlationId);
        markersUsed.add(marker);
        return """
                {"correlationId":"%s","requesterUserId":5,"amount":%s,\
                "eventDate":"2026-07-01","eventLocationId":%d,"requestedEvent":"%s"}"""
                .formatted(correlationId, amount, locationId, marker);
    }

    private void send(String... payloads) {
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafka);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer())) {
            for (String payload : payloads) {
                producer.send(new ProducerRecord<>(TOPIC, "5", payload));
            }
            producer.flush();
        }
    }

    private long requestCount(String marker) {
        return requestRepository.findByRequester_UserId(5).stream()
                .filter(r -> marker.equals(r.getRequestedEvent()))
                .count();
    }

    @Test
    void duplicateDelivery_processedExactlyOnce() {
        String dupId = UUID.randomUUID().toString();
        String marker = "Dedup Test " + dupId;
        String sentinelId = UUID.randomUUID().toString();

        // the same event twice (a redelivery, as after a crash-before-offset-commit), then a
        // sentinel: when the sentinel is marked processed, both duplicates are behind us
        send(eventJson(dupId, 11.11, 1, marker),
             eventJson(dupId, 11.11, 1, marker),
             eventJson(sentinelId, 22.22, 1, "Sentinel " + sentinelId));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertTrue(processedEventRepository.existsById(sentinelId)));

        assertEquals(1, requestCount(marker), "a redelivered correlation id must not create a second request");
        assertTrue(processedEventRepository.existsById(dupId));
    }

    @Test
    void deterministicRejection_isMarkedProcessed_notRetried() {
        String id = UUID.randomUUID().toString();
        String marker = "Rejection Test " + id;

        send(eventJson(id, 33.33, 9999, marker));   // event location 9999 does not exist

        // marked processed = the rejection is FINAL: a replay of this id would be skipped,
        // exactly like the REST twin answering 400 rather than 500
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertTrue(processedEventRepository.existsById(id)));
        assertEquals(0, requestCount(marker));
    }

    @Test
    void poisonAndFailingEvents_deadLettered_consumptionContinues() {
        String failId = UUID.randomUUID().toString();
        String failMarker = "DLT Test " + failId;
        String afterId = UUID.randomUUID().toString();
        String afterMarker = "After Poison " + afterId;

        // poison (never deserializes -> fatal, no retries), then a listener failure (negative
        // amount passes deserialization but violates the DB CHECK -> retried, then dead-lettered),
        // then a good event that must still get through
        send("this is not an event {",
             eventJson(failId, -5.0, 1, failMarker),
             eventJson(afterId, 44.44, 1, afterMarker));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertEquals(1, requestCount(afterMarker), "a poison event must not block the partition"));
        assertFalse(processedEventRepository.existsById(failId),
                "a FAILED event must not be marked processed (the rollback covers the marker)");

        // both casualties must be on the DLT, values intact: the raw bytes for the poison
        // record, the event JSON for the listener failure
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-audit-" + UUID.randomUUID(), "false", embeddedKafka);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> dltConsumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            dltConsumer.subscribe(List.of(DLT));
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(20), 2);
            List<String> values = StreamSupport.stream(records.spliterator(), false)
                    .map(ConsumerRecord::value).toList();
            assertTrue(values.stream().anyMatch(v -> v.contains("not an event")), "poison payload preserved on DLT");
            String failedJson = values.stream().filter(v -> v.contains(failId)).findFirst()
                    .orElseThrow(() -> new AssertionError("failed event JSON preserved on DLT"));
            // replayability: the DLT copy must match the wire contract - ISO date string, not
            // Jackson's default [2026,7,1] int-array (live-caught before this pin existed)
            assertTrue(failedJson.contains("\"2026-07-01\""), "DLT record keeps the contract's ISO date form: " + failedJson);
        }
    }
}
