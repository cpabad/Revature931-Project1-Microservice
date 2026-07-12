package com.revature.ers.auth.repository;

import com.revature.ers.auth.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** The outbox queue: pending rows oldest-first (id order IS publish order - see OutboxRelay). */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findAllByOrderByEventIdAsc();
}
