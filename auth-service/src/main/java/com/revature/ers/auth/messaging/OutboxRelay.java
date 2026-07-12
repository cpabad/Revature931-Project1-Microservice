package com.revature.ers.auth.messaging;

import com.revature.ers.auth.event.UserProfileUpdatedEvent;
import com.revature.ers.auth.model.OutboxEvent;
import com.revature.ers.auth.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import jakarta.annotation.PreDestroy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The broker-side half of the outbox: drains pending rows to Kafka, oldest first, deleting
 * each row only after the broker ACKS it. The 1s poll is the GUARANTEE - it will eventually
 * deliver every committed row no matter what crashed in between; the AFTER_COMMIT nudge is
 * only a latency rescue that asks the relay to run NOW (on the relay's own thread, so a dead
 * broker's send timeout never hangs the caller's HTTP response).
 *
 * Delivery discipline, in order of what it protects:
 *  - blocking send: "published" means the broker ack arrived, not that a buffer accepted it
 *    (the producer's bounded timeouts - max.block/delivery.timeout - cap the wait);
 *  - delete AFTER ack: a crash between the two republishes one event - at-least-once, and
 *    harmless because consumers apply snapshots idempotently;
 *  - STOP on first failure: publishing row N+1 after row N failed would reorder a user's
 *    updates (the exact thing keying by userId promises never happens). The whole queue
 *    waits behind the head; the next tick retries.
 *
 * Single-instance relay: with multiple auth-service replicas, each would poll the same rows
 * and duplicate every event (still converging, but noisy) - production would claim rows with
 * SELECT ... FOR UPDATE SKIP LOCKED or run the relay on one elected instance. Documented,
 * not built: this system runs one auth instance.
 */
@Component
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ExecutorService nudges = Executors.newSingleThreadExecutor();

    public OutboxRelay(OutboxEventRepository outbox, KafkaTemplate<String, String> kafkaTemplate) {
        this.outbox = outbox;
        this.kafkaTemplate = kafkaTemplate;
    }

    @PreDestroy
    void shutdownNudges() {
        nudges.shutdown();
    }

    /** Latency rescue: a commit just enqueued a row - drain immediately instead of in <=1s. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void nudge(UserProfileUpdatedEvent committed) {
        nudges.execute(this::relayPending);
    }

    /** The guarantee: whatever the nudge missed (crash, dead broker, restart) drains here. */
    @Scheduled(fixedDelay = 1000)
    public synchronized void relayPending() {
        for (OutboxEvent pending : outbox.findAllByOrderByEventIdAsc()) {
            try {
                kafkaTemplate.send(pending.getTopic(), pending.getMessageKey(), pending.getPayload())
                        .get(15, TimeUnit.SECONDS);
                outbox.delete(pending);
                LOG.info("outbox row {} delivered to {} (key {})",
                        pending.getEventId(), pending.getTopic(), pending.getMessageKey());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOG.warn("outbox row {} not delivered ({}); stopping this pass to preserve order - next tick retries",
                        pending.getEventId(), e.getMessage());
                return;
            }
        }
    }
}
