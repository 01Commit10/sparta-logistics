package com.hub.inventory;

import com.hub.inventory.domain.stream.*;
import com.hub.inventory.domain.vo.*;
import com.hub.inventory.infrastructure.persistence.PostgresInventoryBatchRepository;
import com.hub.support.TestPostgresConnectConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=false"})
@Import(TestPostgresConnectConfig.class)
class InventoryBatchIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    PostgresInventoryBatchRepository repository;
    UUID epoch, hub, product, inventory, auditor;
    @BeforeEach void setup() {
        repository = new PostgresInventoryBatchRepository(jdbc);
        epoch=UUID.randomUUID(); hub=UUID.randomUUID(); product=UUID.randomUUID(); inventory=UUID.randomUUID(); auditor=UUID.randomUUID();
        jdbc.update("DELETE FROM hub.p_inventory_stream_checkpoints");
        jdbc.update("INSERT INTO hub.p_inventories(id,hub_id,product_id,quantity,created_at) VALUES(?,?,?,100,CURRENT_TIMESTAMP)",inventory,hub,product);
        tx.executeWithoutResult(s -> repository.start(epoch));
    }
    InventoryStreamEvent event(long seq, int quantity, int after) {
        LocalDateTime now=LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        return new InventoryStreamEvent(epoch,seq,UUID.randomUUID(),"batch-"+UUID.randomUUID(),null,
                UUID.randomUUID(),hub,product,inventory,InventoryOperationType.DECREASE,
                InventoryOperationStatus.SUCCEEDED,null,quantity,after,auditor,now,now,auditor,now,auditor);
    }
    StreamEntry entry(InventoryStreamEvent event) { return new StreamEntry(event.sequence()+"-0",event); }
    int quantity() { return jdbc.queryForObject("SELECT quantity FROM hub.p_inventories WHERE id=?",Integer.class,inventory); }
    long operations() { return jdbc.queryForObject("SELECT COUNT(*) FROM hub.p_inventory_operations WHERE inventory_id=?",Long.class,inventory); }
    @Test void commitsBatchAndReplayExactlyOnceWithCallerAudit() {
        List<StreamEntry> batch=List.of(entry(event(1,30,70)),entry(event(2,20,50)));
        tx.executeWithoutResult(s -> repository.persist(batch));
        tx.executeWithoutResult(s -> repository.persist(batch));
        assertThat(quantity()).isEqualTo(50); assertThat(operations()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hub.p_inventory_histories WHERE inventory_id=? AND created_by=?",Long.class,inventory,auditor)).isEqualTo(2);
        assertThat(repository.checkpoint().orElseThrow().sequence()).isEqualTo(2);
    }
    @Test void historyFailureRollsBackStockOperationsAndCheckpoint() {
        InventoryStreamEvent first=event(1,30,70);
        jdbc.execute("ALTER TABLE hub.p_inventory_histories ADD CONSTRAINT test_batch_history CHECK(inventory_id <> '"+inventory+"')");
        try {
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> repository.persist(List.of(entry(first))))).isInstanceOf(RuntimeException.class);
            assertThat(quantity()).isEqualTo(100); assertThat(operations()).isZero();
            assertThat(repository.checkpoint().orElseThrow().sequence()).isZero();
        } finally { jdbc.execute("ALTER TABLE hub.p_inventory_histories DROP CONSTRAINT test_batch_history"); }
    }
    @Test void refusesSequenceGapOrForgedRemainingQuantity() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> repository.persist(List.of(entry(event(2,30,70)))))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> repository.persist(List.of(entry(event(1,30,99)))))).isInstanceOf(RuntimeException.class);
        assertThat(quantity()).isEqualTo(100); assertThat(operations()).isZero();
    }
    @Test void persistsAcceptedEventWithoutReactivatingDeletedInventory() {
        jdbc.update("UPDATE hub.p_inventories SET deleted_at=CURRENT_TIMESTAMP,deleted_by=?,updated_by=? WHERE id=?",auditor,auditor,inventory);
        tx.executeWithoutResult(s -> repository.persist(List.of(entry(event(1,30,70)))));
        assertThat(quantity()).isEqualTo(70);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL AND deleted_by=? AND updated_by=? FROM hub.p_inventories WHERE id=?",Boolean.class,auditor,auditor,inventory)).isTrue();
    }

    @Test void rejectsRestoreReferencingALaterEventInTheSameBatch() {
        InventoryStreamEvent original=event(2,30,100);
        InventoryStreamEvent restore=new InventoryStreamEvent(epoch,1,UUID.randomUUID(),"future-restore-"+UUID.randomUUID(),
                original.operationId(),original.orderId(),hub,product,inventory,InventoryOperationType.RESTORE,
                InventoryOperationStatus.SUCCEEDED,null,30,130,auditor,original.occurredAt(),original.createdAt(),auditor,original.updatedAt(),auditor);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> repository.persist(List.of(entry(restore),entry(original)))))
                .isInstanceOf(RuntimeException.class);
        assertThat(quantity()).isEqualTo(100); assertThat(operations()).isZero();
    }

    @Test void refusesShortageEventWhoseSnapshotDisagreesWithStock() {
        InventoryStreamEvent sample=event(1,1,999);
        InventoryStreamEvent forged=new InventoryStreamEvent(epoch,1,sample.operationId(),sample.idempotencyKey(),null,
                sample.orderId(),hub,product,inventory,InventoryOperationType.DECREASE,InventoryOperationStatus.REJECTED,
                InventoryRejectionReason.INSUFFICIENT_STOCK,1,999,auditor,sample.occurredAt(),sample.createdAt(),auditor,sample.updatedAt(),auditor);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> repository.persist(List.of(entry(forged)))))
                .isInstanceOf(RuntimeException.class);
        assertThat(operations()).isZero();
        assertThat(repository.checkpoint().orElseThrow().sequence()).isZero();
    }

    @Test void replayCannotChangeTheOriginalStockActorOrTimestamp() {
        InventoryStreamEvent accepted=event(1,30,70);
        tx.executeWithoutResult(s -> repository.persist(List.of(entry(accepted))));
        InventoryStreamEvent altered=new InventoryStreamEvent(epoch,1,accepted.operationId(),accepted.idempotencyKey(),null,
                accepted.orderId(),hub,product,inventory,accepted.operationType(),accepted.operationStatus(),null,30,70,
                UUID.randomUUID(),accepted.occurredAt().plusSeconds(1),accepted.createdAt(),auditor,accepted.updatedAt(),auditor);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> repository.persist(List.of(entry(altered)))))
                .isInstanceOf(RuntimeException.class);
        assertThat(quantity()).isEqualTo(70); assertThat(operations()).isEqualTo(1);
    }
}
