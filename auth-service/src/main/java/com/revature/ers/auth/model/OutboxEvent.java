package com.revature.ers.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One not-yet-delivered event (transactional outbox). Written by OutboxWriter in the SAME
 * transaction as the domain change it announces - so "the update committed" and "the event
 * will be delivered" are one atomic fact, not two writes that can disagree (the dual-write
 * problem the old AFTER_COMMIT direct publish had). Deleted by OutboxRelay on the broker's
 * ack: a row's existence means "pending", and the table is normally empty.
 *
 * The payload is the serialized wire JSON, not a reference to domain state - the event must
 * carry what was true AT COMMIT TIME even if the row changes again before the relay runs
 * (two rapid renames = two rows, each with its own snapshot, delivered in id order).
 */
@Entity
@Table(name = "outbox_event", schema = "\"ExpenseReimbursementManagementSystem\"")
@Data
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "eventid")
    private Long eventId;

    @Column(name = "topic")
    private String topic;

    @Column(name = "messagekey")
    private String messageKey;

    @Column(name = "payload")
    private String payload;

    @Column(name = "createdat")
    private Instant createdAt;

    public OutboxEvent(String topic, String messageKey, String payload) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.createdAt = Instant.now();
    }
}
