package com.hub.inventory.application.service;

import com.hub.inventory.domain.stream.*;
import com.hub.inventory.domain.vo.InventoryOperationStatus;
import com.hub.inventory.domain.vo.InventoryOperationType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryStreamConsumerTest {
    @Test
    void persistsTheAvailableBatchWithoutCollectingMoreAndAcknowledgesAfterCommit() {
        UUID epoch = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        InventoryStreamEvent event = new InventoryStreamEvent(epoch, 1, UUID.randomUUID(), "available-batch", null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                InventoryOperationType.DECREASE, InventoryOperationStatus.SUCCEEDED, null,
                1, 99, null, now, now, null, now, null);
        List<StreamEntry> available = List.of(new StreamEntry("1-0", event));
        List<String> steps = new ArrayList<>();
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        InventoryBatchRepository batches = mock(InventoryBatchRepository.class);
        InventoryPipelineService pipeline = mock(InventoryPipelineService.class);
        when(pipeline.withReady(any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
        when(reservations.read(500)).thenReturn(available);
        when(batches.persist(available)).thenAnswer(call -> {
            steps.add("persist");
            return new PipelineCheckpoint(epoch, 1, "1-0");
        });
        doAnswer(call -> { steps.add("ACK"); return null; })
                .when(reservations).confirm(epoch, 1, "1-0");
        PlatformTransactionManager manager = new PlatformTransactionManager() {
            public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
            public void commit(TransactionStatus status) { steps.add("COMMIT"); }
            public void rollback(TransactionStatus status) { steps.add("ROLLBACK"); }
        };
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        try {
            InventoryStreamConsumer consumer = new InventoryStreamConsumer(reservations, batches, pipeline,
                    new TransactionTemplate(manager), metrics, false, 500);

            assertThat(consumer.consumeOnce()).isEqualTo(1);

            assertThat(steps).containsExactly("persist", "COMMIT", "ACK");
            verify(reservations).read(500);
            verify(reservations).confirm(epoch, 1, "1-0");
            verifyNoMoreInteractions(reservations);
        } finally { metrics.close(); }
    }
}
