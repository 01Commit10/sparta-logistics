package com.hub.inventory.application.service;

import com.hub.inventory.domain.stream.*;
import io.micrometer.core.instrument.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/** 읽은 묶음을 추가 수집 대기 없이 저장한다. DB 커밋 후 ACK하며 파이프라인당 Consumer는 하나다. */
@Service
@ConditionalOnProperty(name="inventory.stock.mode",havingValue="redis-streams")
public class InventoryStreamConsumer {
    private static final Logger log=LoggerFactory.getLogger(InventoryStreamConsumer.class);
    private final InventoryReservationRepository reservations;
    private final InventoryBatchRepository batches;
    private final InventoryPipelineService pipeline;
    private final TransactionTemplate tx;
    private final boolean enabled;
    private final int maxBatch;
    private volatile boolean running;
    private Thread worker;
    private final ReentrantLock consumerLock=new ReentrantLock();
    private final DistributionSummary batchSizes;
    private final io.micrometer.core.instrument.Timer completion;
    private final Counter commits,failures;
    private volatile String lastFailure;
    private volatile long lastCommitMillis;
    private long lastLogNanos;
    public InventoryStreamConsumer(InventoryReservationRepository reservations,InventoryBatchRepository batches,
            InventoryPipelineService pipeline,TransactionTemplate tx,MeterRegistry metrics,
            @Value("${inventory.streams.consumer-enabled:true}") boolean enabled,
            @Value("${inventory.streams.batch-size:500}") int maxBatch) {
        if(maxBatch<1 || maxBatch>500) throw new IllegalArgumentException("Invalid inventory batch limit");
        this.reservations=reservations; this.batches=batches; this.pipeline=pipeline; this.tx=tx;
        this.enabled=enabled; this.maxBatch=maxBatch;
        this.batchSizes=DistributionSummary.builder("inventory.stream.batch.size").register(metrics);
        this.completion=io.micrometer.core.instrument.Timer.builder("inventory.stream.persistence").publishPercentiles(.5,.95,.99).register(metrics);
        Gauge.builder("inventory.stream.last.commit.time",this,consumer -> consumer.lastCommitMillis).register(metrics);
        this.commits=metrics.counter("inventory.stream.commits"); this.failures=metrics.counter("inventory.stream.failures");
        Gauge.builder("inventory.stream.backlog",reservations,r -> { try { return r.state().backlog(); } catch(RuntimeException e) { return Double.NaN; } }).register(metrics);
    }
    // 파이프라인 초기화가 끝난 뒤 전용 스레드 하나를 시작한다.
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if(!enabled || (worker!=null && worker.isAlive())) return;
        running=true;
        worker=new Thread(this::consumeContinuously,"inventory-stream-consumer");
        worker.setDaemon(true);
        worker.start();
    }
    private void consumeContinuously() {
        try {
            while(running && !Thread.currentThread().isInterrupted()) {
                if(consumeOnce()==0) {
                    // 빈 읽기·동시 수동 실행·장애에서 반복 호출로 CPU를 소모하지 않는다.
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(lastFailure==null ? 1 : 100));
                }
            }
        } finally { running=false; }
    }
    // 정상 처리 중이면 먼저 커밋·ACK를 마치게 하고, 종료가 지연될 때만 중단을 요청한다.
    @PreDestroy
    public synchronized void stop() {
        running=false;
        if(worker==null) return;
        try {
            worker.join(5_000);
            if(worker.isAlive()) {
                worker.interrupt();
                worker.join(1_000);
                if(worker.isAlive()) log.warn("Inventory Stream Consumer has not stopped; unacknowledged events remain retryable");
            }
        } catch(InterruptedException e) {
            worker.interrupt();
            Thread.currentThread().interrupt();
        }
    }
    public int consumeOnce() {
        if(!consumerLock.tryLock()) return 0;
        try { return pipeline.withReady(this::consumeReady); }
        catch(RuntimeException e) {
            failures.increment(); lastFailure=e.toString();
            long now=System.nanoTime();
            if(now-lastLogNanos>TimeUnit.SECONDS.toNanos(5)) { lastLogNanos=now; log.warn("Inventory Stream paused/retrying without ACK: {}",lastFailure); }
            return 0;
        } finally { consumerLock.unlock(); }
    }
    private int consumeReady() {
        List<StreamEntry> entries;
        try { entries=reservations.read(maxBatch); }
        catch(InvalidInventoryStreamEventException failure) {
            commitValidPrefix(failure.validPrefix());
            throw failure;
        }
        if(entries.isEmpty()) { lastFailure=null; return 0; }
        commitValidPrefix(entries);
        lastFailure=null;
        return entries.size();
    }
    private void commitValidPrefix(List<StreamEntry> entries) {
        if(entries.isEmpty()) return;
        try { commit(entries); }
        catch(RuntimeException failure) {
            // 정상인 앞부분만 확정하고, 실패한 메시지부터는 Pending에 남겨 재처리한다.
            if(entries.size()>1) for(var entry:entries) commit(List.of(entry));
            else throw failure;
        }
    }
    private void commit(List<StreamEntry> entries) {
        PipelineCheckpoint checkpoint=tx.execute(s -> batches.persist(entries));
        // DB 커밋이 완료된 뒤 ACK한다. ACK가 실패해도 Pending 재처리로 복구할 수 있다.
        lastCommitMillis=System.currentTimeMillis();
        commits.increment(); batchSizes.record(entries.size());
        LocalDateTime committed=LocalDateTime.now();
        for(var entry:entries) completion.record(Duration.between(entry.event().occurredAt(),committed).abs());
        reservations.confirm(checkpoint.epoch(),checkpoint.sequence(),checkpoint.streamId());
    }
    public Optional<String> lastFailure() { return Optional.ofNullable(lastFailure); }
}
