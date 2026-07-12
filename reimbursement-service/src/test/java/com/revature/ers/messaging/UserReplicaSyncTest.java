package com.revature.ers.messaging;

import com.revature.ers.model.User;
import com.revature.ers.repository.UserRepository;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * The consume side of the replica sync: raw JSON on auth.user.updated (the literal mirrors
 * the producing side's record - the JSON is the contract, no shared library) overwrites the
 * local users replica. Also pins the two deliberate behaviors: replaying the same snapshot
 * converges (idempotent by operation shape - no processed_event ledger on this topic), and
 * an unknown user is skipped without killing consumption (no user-created sync exists).
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = "auth.user.updated")
class UserReplicaSyncTest {

    private static final String TOPIC = "auth.user.updated";

    @Autowired private EmbeddedKafkaBroker embeddedKafka;
    @Autowired private UserRepository userRepository;

    private User original;

    @BeforeEach
    void rememberUser5() {
        original = userRepository.findById(5).orElseThrow();
    }

    @AfterEach
    void restoreUser5() {
        User row = userRepository.findById(5).orElseThrow();
        row.setUsername(original.getUsername());
        row.setEmail(original.getEmail());
        row.setFirstName(original.getFirstName());
        row.setLastName(original.getLastName());
        userRepository.save(row);
    }

    private void send(String key, String json) {
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafka);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(TOPIC, key, json));
            producer.flush();
        }
    }

    private String snapshotJson(int userId, String username, String email) {
        return """
                {"userId":%d,"username":"%s","email":"%s","firstName":"%s","lastName":"%s","role":"Employee"}"""
                .formatted(userId, username, email, original.getFirstName(), original.getLastName());
    }

    @Test
    void snapshotEvent_overwritesReplica_unknownUserSkipped_replayConverges() {
        // an unknown user first: must be skipped, not dead-lettered, not fatal to the partition
        send("999", snapshotJson(999, "ghost", "ghost@ers.local"));

        // then the real snapshot - consuming it proves the unknown one didn't block anything
        String renamed = "employee4renamed";
        send("5", snapshotJson(5, renamed, "replica-sync@ers.local"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            User replica = userRepository.findById(5).orElseThrow();
            assertEquals(renamed, replica.getUsername(), "the rename propagated from auth-service's event");
            assertEquals("replica-sync@ers.local", replica.getEmail());
        });

        // replay the SAME snapshot: at-least-once redelivery. No ledger on this topic -
        // overwriting with identical state converges to the identical row.
        send("5", snapshotJson(5, renamed, "replica-sync@ers.local"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertEquals(renamed, userRepository.findById(5).orElseThrow().getUsername()));

        assertEquals(0, userRepository.findAll().stream().filter(u -> u.getUserId() == 999).count(),
                "unknown users are never invented into the replica");
    }
}
