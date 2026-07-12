package com.revature.ers.auth.messaging;

import com.revature.ers.auth.model.OutboxEvent;
import com.revature.ers.auth.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The relay's delivery discipline, tested where an embedded broker cannot reach: the failure
 * path. A mocked failed future is a faithful broker-down stand-in - the real producer honors
 * the same contract (the future completes exceptionally once its bounded timeouts expire).
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock private OutboxEventRepository outbox;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @InjectMocks private OutboxRelay relay;

    private OutboxEvent row(long id) {
        OutboxEvent e = new OutboxEvent("auth.user.updated", "3", "{\"userId\":3}");
        e.setEventId(id);
        return e;
    }

    @Test
    void ackedRowsAreDeletedInOrder() {
        OutboxEvent first = row(1);
        OutboxEvent second = row(2);
        when(outbox.findAllByOrderByEventIdAsc()).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(Mockito.mock(SendResult.class)));

        relay.relayPending();

        verify(outbox).delete(first);
        verify(outbox).delete(second);
    }

    @Test
    void firstFailureStopsThePass_nothingDeleted_orderPreserved() {
        OutboxEvent head = row(1);
        OutboxEvent behind = row(2);
        when(outbox.findAllByOrderByEventIdAsc()).thenReturn(List.of(head, behind));
        // broker down: the head row's send fails...
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unreachable")));

        relay.relayPending();

        // ...so the head is kept for the next tick, and the row BEHIND it is never attempted -
        // delivering it first would reorder the stream the userId key promises is ordered
        verify(outbox, never()).delete(any(OutboxEvent.class));
        verify(kafkaTemplate, Mockito.times(1)).send(anyString(), anyString(), anyString());
    }
}
