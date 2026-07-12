package com.revature.ers.auth.messaging;

import com.revature.ers.auth.model.User;
import com.revature.ers.auth.repository.UserRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The publish side of the replica sync: a COMMITTED profile update - and only a committed
 * one - lands on auth.user.updated as a full snapshot.
 *
 * Deliberately NOT @Transactional (unlike UserControllerTest): the publisher runs at
 * AFTER_COMMIT, and a rolled-back test transaction never commits - the listener would
 * simply not fire and the test would pass vacuously with a broken publisher. Real commits
 * mean manual cleanup: the original hash and email are captured and restored.
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = "auth.user.updated")
class UserUpdatedPublishTest {

    private static final String KNOWN_PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmbeddedKafkaBroker embeddedKafka;

    private String originalHash;
    private String originalEmail;

    @BeforeEach
    void stampKnownPasswordAndRemember() {
        User user = userRepository.findById(3).orElseThrow();
        originalHash = user.getPassword();
        originalEmail = user.getEmail();
        user.setPassword(passwordEncoder.encode(KNOWN_PASSWORD));
        userRepository.save(user);
    }

    @AfterEach
    void restoreRow() {
        User user = userRepository.findById(3).orElseThrow();
        user.setPassword(originalHash);
        user.setEmail(originalEmail);
        userRepository.save(user);   // plain repository save - raises no domain event
    }

    @Test
    void committedUpdatePublishes_rejectedUpdateDoesNot() throws Exception {
        // 1) rejected: wrong current password -> 403, nothing committed, nothing published
        mockMvc.perform(put("/users/me")
                        .with(jwt().jwt(j -> j.subject("3")).authorities(new SimpleGrantedAuthority("ROLE_Employee")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"not-it\", \"newEmail\": \"never@ers.local\"}"))
                .andExpect(status().isForbidden());

        // 2) committed: real update -> 200 and one event
        mockMvc.perform(put("/users/me")
                        .with(jwt().jwt(j -> j.subject("3")).authorities(new SimpleGrantedAuthority("ROLE_Employee")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"" + KNOWN_PASSWORD + "\", \"newEmail\": \"sync-test@ers.local\"}"))
                .andExpect(status().isOk());

        Map<String, Object> props = KafkaTestUtils.consumerProps("publish-audit-" + UUID.randomUUID(), "false", embeddedKafka);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("auth.user.updated"));
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(15), 1);
            List<ConsumerRecord<String, String>> all = StreamSupport.stream(records.spliterator(), false).toList();

            assertEquals(1, all.size(), "exactly the committed update publishes - the 403 must not");
            ConsumerRecord<String, String> record = all.get(0);
            assertEquals("3", record.key(), "keyed by userId so one user's updates stay ordered");
            String json = record.value();
            assertTrue(json.contains("\"userId\":3"));
            assertTrue(json.contains("\"email\":\"sync-test@ers.local\""), "full snapshot carries the new state");
            assertTrue(json.contains("\"username\":"), "snapshot carries ALL replicated fields, not a delta");
            assertTrue(json.contains("\"role\":"));
            assertFalse(json.contains("password"), "the credential never rides an event");
        }
    }
}
