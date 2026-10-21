package com.hub.inventory.application.service;

import com.hub.inventory.application.dto.InventoryResult;
import com.hub.inventory.domain.stream.*;
import com.hub.inventory.domain.vo.InventoryOperationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/** Coordinates DB snapshots and Redis outside DB transactions; the gate serializes local recovery only. */
@Service
@ConditionalOnProperty(name="inventory.stock.mode",havingValue="redis-streams")
public class InventoryPipelineService implements ApplicationRunner,InventoryLiveState {
    private final InventoryBatchRepository batches;
    private final InventoryReservationRepository reservations;
    private final TransactionTemplate tx;
    private final boolean recoveryRequested;
    private final ReentrantReadWriteLock gate=new ReentrantReadWriteLock(true);
    private volatile boolean initialized;
    public InventoryPipelineService(InventoryBatchRepository batches,InventoryReservationRepository reservations,
            TransactionTemplate tx,@Value("${inventory.streams.recover:false}") boolean recoveryRequested) {
        this.batches=batches; this.reservations=reservations; this.tx=tx; this.recoveryRequested=recoveryRequested;
    }
    @Override public void run(ApplicationArguments args) {
        var state=reservations.state();
        boolean newPipeline=tx.execute(s -> batches.checkpoint().isEmpty());
        if(!newPipeline && state.status()==PipelineState.Status.MISSING)
            throw new IllegalStateException("Active inventory pipeline lost Redis data; automatic stale DB reload is forbidden");
        PipelineSnapshot snapshot=tx.execute(s -> {
            if(newPipeline) batches.start(UUID.randomUUID());
            return batches.snapshot(UUID.randomUUID());
        });
        var checkpoint=tx.execute(s -> batches.checkpoint().orElseThrow());
        if(checkpoint.recoveryEpoch()!=null) {
            recover();
            return;
        }
        if(state.status()!=PipelineState.Status.MISSING && !Objects.equals(state.epoch(),snapshot.epoch()))
            throw new IllegalStateException("Redis and PostgreSQL inventory epochs disagree; explicit reconciliation required");
        if(state.status()==PipelineState.Status.MISSING || state.status()==PipelineState.Status.READY) {
            reservations.bootstrap(snapshot);
            // A previous process may have committed PostgreSQL and stopped before ACK.
            reservations.confirm(snapshot.epoch(),snapshot.sequence(),snapshot.streamId());
        }
        initialized=true;
        if(recoveryRequested) recover();
    }
    public InventoryStreamEvent reserve(ReservationRequest request) {
        return withReady(() -> {
            if(request.operationType()==InventoryOperationType.DECREASE && !reservations.inventoryKnown(request.hubId(),request.productId())) {
                Optional<InventorySeed> seed=tx.execute(s -> batches.inventory(request.hubId(),request.productId()));
                seed.ifPresent(reservations::loadInventory);
            }
            return reservations.execute(request);
        });
    }
    public <T> T withReady(Supplier<T> action) {
        gate.readLock().lock();
        try {
            if(!initialized) throw new InventoryPipelineException(InventoryPipelineException.Reason.UNAVAILABLE);
            return action.get();
        } finally { gate.readLock().unlock(); }
    }
    @Override public List<InventoryResult> refresh(List<InventoryResult> inventories) {
        try { return withReady(() -> {
            List<InventoryResult> active=new ArrayList<>();
            for(var value:inventories) {
                if(reservations.isProductDeleted(value.productId())) continue;
                if(!reservations.inventoryKnown(value.hubId(),value.productId())) reservations.loadInventory(seed(value));
                active.add(value);
            }
            var quantities=reservations.quantities(active.stream().map(InventoryResult::id).toList());
            return active.stream().map(v -> new InventoryResult(v.id(),v.hubId(),v.productId(),quantities.get(v.id()))).toList();
        }); } catch(InventoryPipelineException e) { throw InventoryPipelineErrors.translate(e); }
    }
    @Override public void registered(InventoryResult value) {
        try { withReady(() -> { reservations.loadInventory(seed(value)); return null; }); }
        catch(InventoryPipelineException e) { throw InventoryPipelineErrors.translate(e); }
    }
    @Override public void beforeDelete(UUID productId) {
        try { withReady(() -> { reservations.tombstoneProduct(productId); return null; }); }
        catch(InventoryPipelineException e) { throw InventoryPipelineErrors.translate(e); }
    }
    private InventorySeed seed(InventoryResult value) { return new InventorySeed(value.id(),value.hubId(),value.productId(),value.quantity(),false); }

    /** Explicit recovery: block this Hub's requests and Consumer, replay only complete uncommitted events. */
    public void recover() {
        gate.writeLock().lock();
        initialized=false;
        try {
            var previous=reservations.state();
            if(previous.status()==PipelineState.Status.MISSING)
                throw new IllegalStateException("Redis event log is missing; reconcile externally before recovery");
            var checkpoint=tx.execute(s -> batches.checkpoint().orElseThrow());
            // A durable intent proves that this READY epoch was built from the previous DB checkpoint.
            // A crash before the DB switch can therefore finish the same handoff without replaying stock.
            if(checkpoint.recoveryEpoch()!=null && checkpoint.recoveryEpoch().equals(previous.epoch())
                    && previous.status()==PipelineState.Status.READY) {
                tx.executeWithoutResult(s -> batches.switchEpoch(checkpoint.epoch(),previous.epoch()));
                initialized=true;
                return;
            }
            if(!checkpoint.epoch().equals(previous.epoch()))
                throw new IllegalStateException("Epoch mismatch requires explicit operator reconciliation");
            reservations.pause();
            var snapshot=tx.execute(s -> batches.snapshot(checkpoint.epoch()));
            var complete=reservations.unconfirmed();
            UUID next=UUID.randomUUID();
            // Preserve the old checkpoint until the complete new Redis epoch is ready.
            tx.executeWithoutResult(s -> batches.prepareRecovery(snapshot.epoch(),next));
            reservations.rebuild(snapshot,complete,next);
            tx.executeWithoutResult(s -> batches.switchEpoch(snapshot.epoch(),next));
            initialized=true;
        } finally { gate.writeLock().unlock(); }
    }
    /** Compare only a stable, fully persisted revision. Concurrent new requests mean retry, not corruption. */
    public boolean verifyDrained() {
        return withReady(() -> {
            var before=reservations.state();
            if(before.status()!=PipelineState.Status.READY || before.backlog()!=0) return false;
            var snapshot=tx.execute(s -> batches.snapshot(before.epoch()));
            var quantities=reservations.quantities(snapshot.inventories().stream().map(InventorySeed::inventoryId).toList());
            var after=reservations.state();
            if(!before.equals(after) || snapshot.sequence()!=after.confirmedSequence() || !snapshot.epoch().equals(after.epoch())) return false;
            boolean valid=snapshot.inventories().stream().allMatch(v -> Objects.equals(v.quantity(),quantities.get(v.inventoryId())));
            if(!valid) reservations.pause();
            return valid;
        });
    }
}
