package com.revature.ers.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Idempotency ledger for the Kafka intake. Kafka delivers at-least-once: a crash between the
 * database commit and the offset commit replays the event, so the listener writes one row per
 * correlation id in the SAME transaction as the domain write and skips ids already present.
 * A row means "this event's outcome is final" - created a request, or was deterministically
 * rejected; either way a redelivery must not run the fan-out again.
 */
@Entity
@Table(name = "processed_event", schema = "\"ExpenseReimbursementManagementSystem\"")
@Data
@NoArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "correlationid")
    private String correlationId;

    @Column(name = "processedat")
    private Instant processedAt;

    public ProcessedEvent(String correlationId) {
        this.correlationId = correlationId;
        this.processedAt = Instant.now();
    }
}
