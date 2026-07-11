package com.revature.ers.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.revature.ers.event.RequestSubmissionEvent;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

/**
 * Failure policy for the Kafka intake: retry transient faults, dead-letter the rest.
 *
 * The listener used to catch-and-drop failures, which loses events. Now exceptions reach the
 * {@link DefaultErrorHandler} below: 3 total attempts 1s apart (a database blip heals itself),
 * then the {@link DeadLetterPublishingRecoverer} publishes the failed record - unchanged, with
 * diagnostic headers (exception class, message, original topic/partition/offset) - to
 * {@code reimbursement.request.submitted.DLT} and consumption moves on. Without this, one
 * poison event blocks its partition forever (head-of-line blocking); with it, the DLT is the
 * operator's inbox: every record there needs a human, nothing there was silently lost.
 *
 * Deserialization failures (malformed JSON that never becomes an event object) are surfaced by
 * the ErrorHandlingDeserializer wrapper (application.properties), classified as fatal - a
 * retry cannot fix a parse error - and go straight to the DLT on the first attempt.
 *
 * The DLT producer serializes by payload type: a listener failure carries the deserialized
 * event (JSON), a deserialization failure carries the original raw {@code byte[]} - the
 * DelegatingByTypeSerializer handles both, so the DLT record is always a faithful copy.
 */
@Configuration
public class KafkaConfig {

    /** Boot wires the single CommonErrorHandler bean into the auto-configured listener container. */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> deadLetterTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetterTemplate);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L)); // 1 try + 2 retries
    }

    @Bean
    public KafkaTemplate<Object, Object> deadLetterTemplate(ProducerFactory<Object, Object> deadLetterProducerFactory) {
        return new KafkaTemplate<>(deadLetterProducerFactory);
    }

    @Bean
    public ProducerFactory<Object, Object> deadLetterProducerFactory(KafkaProperties kafkaProperties) {
        // Dates must serialize as "2026-07-01" (the wire contract), not Jackson's default
        // [2026,7,1] int-array - otherwise a DLT record could not be replayed onto the main
        // topic and parse. (Live-caught: the default mapper produced the array form.)
        ObjectMapper dltMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        // The delegate map matches EXACT types: a listener failure carries the deserialized
        // event object, a deserialization failure carries the raw byte[]. A new event type
        // consumed by this service must be added here or its DLT publication will fail.
        return new DefaultKafkaProducerFactory<Object, Object>(
                kafkaProperties.buildProducerProperties(null),
                new DelegatingByTypeSerializer(Map.of(
                        byte[].class, new ByteArraySerializer(),
                        String.class, new StringSerializer())),
                new DelegatingByTypeSerializer(Map.of(
                        byte[].class, new ByteArraySerializer(),
                        RequestSubmissionEvent.class, new JsonSerializer<RequestSubmissionEvent>(dltMapper))));
    }
}
