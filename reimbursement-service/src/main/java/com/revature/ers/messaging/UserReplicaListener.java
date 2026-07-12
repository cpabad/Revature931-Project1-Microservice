package com.revature.ers.messaging;

import com.revature.ers.event.UserProfileUpdatedEvent;
import com.revature.ers.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the local users replica converged with auth-service (the owner): each event carries
 * the full replicated state, and this listener overwrites the row with it - event-carried
 * state transfer. This closes the gap the README used to document as future work: a profile
 * rename in auth-service now propagates here eventually instead of never.
 *
 * NO processed_event ledger on this topic - deliberately, and the contrast with the request
 * intake is the point: the submission fan-out CREATES rows, so replaying it duplicates state
 * and needs bookkeeping; overwriting a row with a snapshot converges to the same row no
 * matter how many times it replays. Idempotency comes from the operation's shape, not from
 * a table. Per-user ordering comes from the producer keying events by userId (one partition
 * per key), so the latest event to arrive is the latest state.
 *
 * The role field rides the event for contract completeness but is not applied: roles are
 * immutable reference data with no mutation path anywhere in the system, and the replica's
 * role is a FK this event's role NAME would need a lookup to resolve. Unknown users are
 * logged and skipped: user CREATION sync is a separate contract this system does not have
 * (the seed created both copies) - inventing half of it here would hide that gap.
 */
@Component
public class UserReplicaListener {

    private static final Logger LOG = LoggerFactory.getLogger(UserReplicaListener.class);

    private final UserRepository userRepository;

    public UserReplicaListener(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @KafkaListener(topics = "${ers.kafka.topic.user-updated}", groupId = "ers-reimbursement",
            // this topic carries a different event type than the intake topic; the listener
            // overrides the consumer's default deserialization target for its own records
            properties = "spring.json.value.default.type=com.revature.ers.event.UserProfileUpdatedEvent")
    @Transactional
    public void onUserUpdated(UserProfileUpdatedEvent event) {
        userRepository.findById(event.userId()).ifPresentOrElse(replica -> {
            replica.setUsername(event.username());
            replica.setEmail(event.email());
            replica.setFirstName(event.firstName());
            replica.setLastName(event.lastName());
            userRepository.save(replica);
            LOG.info("replica user {} converged from auth.user.updated", event.userId());
        }, () -> LOG.warn("user.updated for unknown user {} skipped - no user-created sync exists (seed-only replica)",
                event.userId()));
    }
}
