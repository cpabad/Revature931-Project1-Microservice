package com.revature.ers.auth.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.revature.ers.auth.event.UserProfileUpdatedEvent;
import com.revature.ers.auth.model.OutboxEvent;
import com.revature.ers.auth.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction-side half of the outbox: turns the domain event into a pending outbox row
 * IN THE CALLER'S TRANSACTION. UserService still just raises its event - it learns nothing
 * about outboxes or Kafka; only this bridge changed when the delivery guarantee did.
 *
 * Propagation.MANDATORY makes the guarantee an assertion, not a hope: this listener refuses
 * to run without an active transaction to join (it throws), because an outbox row written in
 * its OWN transaction would recreate exactly the dual-write it exists to kill. A rollback of
 * the profile update takes the outbox row down with it - nothing is ever announced that did
 * not commit.
 *
 * The event is serialized to its wire JSON here, at commit time: if the user renames twice
 * quickly, each transaction enqueues its own snapshot, and the relay delivers them in id
 * order - the replica converges through the same states in the same order.
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;
    private final String topic;

    public OutboxWriter(OutboxEventRepository outbox, ObjectMapper objectMapper,
                        @Value("${ers.kafka.topic.user-updated}") String topic) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onProfileUpdated(UserProfileUpdatedEvent event) {
        try {
            outbox.save(new OutboxEvent(topic, String.valueOf(event.userId()),
                    objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            // a record of five strings and an int cannot fail to serialize; if it somehow
            // does, failing the update is CORRECT - committing it unannounced would silently
            // desync every replica
            throw new IllegalStateException("could not enqueue user.updated for user " + event.userId(), e);
        }
    }
}
