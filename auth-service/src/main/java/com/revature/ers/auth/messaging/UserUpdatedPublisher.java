package com.revature.ers.auth.messaging;

import com.revature.ers.auth.event.UserProfileUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the domain to Kafka WITHOUT the service knowing Kafka exists: UserService raises a
 * Spring application event inside its transaction; this listener runs at AFTER_COMMIT, so an
 * update that rolls back is never announced (publishing inside the transaction would tell the
 * world about state that might not survive).
 *
 * The event is keyed by userId: Kafka guarantees order within a partition, and the key pins
 * every update for one user to the same partition - a rename then a re-rename arrive in the
 * order they happened. Send failures are logged, not thrown: the REST caller's update DID
 * commit, so failing their request over a broker hiccup would be a lie in the other direction.
 * The honest gap - commit succeeds, then this publish is lost before it reaches the broker -
 * is the classic dual-write problem; the production fix is a transactional outbox (ROADMAP),
 * and until then the replica heals on the user's next profile update.
 */
@Component
public class UserUpdatedPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(UserUpdatedPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public UserUpdatedPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                @Value("${ers.kafka.topic.user-updated}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProfileUpdated(UserProfileUpdatedEvent event) {
        kafkaTemplate.send(topic, String.valueOf(event.userId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOG.error("user {} profile update committed but the sync event was NOT published - "
                                + "replicas are stale until the next update (dual-write gap; outbox is the fix)",
                                event.userId(), ex);
                    } else {
                        LOG.info("published user.updated for user {} (partition {})",
                                event.userId(), result.getRecordMetadata().partition());
                    }
                });
    }
}
