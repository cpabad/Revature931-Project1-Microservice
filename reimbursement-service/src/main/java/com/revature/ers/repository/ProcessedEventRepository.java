package com.revature.ers.repository;

import com.revature.ers.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/** The Kafka intake's idempotency ledger; existsById + save is all the listener needs. */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
