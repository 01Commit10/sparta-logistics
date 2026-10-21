package com.hub.inventory.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hub.inventory.domain.stream.*;
import com.hub.inventory.domain.vo.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RedisInventoryReservationTest {
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4"))
            .withExposedPorts(6379).withCommand("redis-server", "--appendonly", "yes", "--appendfsync", "always", "--maxmemory-policy", "noeviction");
    StringRedisTemplate redis;
    RedisInventoryReservationRepository repository;
    LettuceConnectionFactory connection;
    final UUID inventory = UUID.randomUUID(), hub = UUID.randomUUID(), product = UUID.randomUUID();
    UUID epoch;
    @BeforeAll static void startRedis() { REDIS.start(); }
    @AfterAll static void stopRedis() { REDIS.stop(); }
    static int redisPort() {
        var inspected=REDIS.getDockerClient().inspectContainerCmd(REDIS.getContainerId()).exec();
        return Integer.parseInt(inspected.getNetworkSettings().getPorts().getBindings().get(new com.github.dockerjava.api.model.ExposedPort(6379))[0].getHostPortSpec());
    }
    @BeforeEach void setup() {
        connection = new LettuceConnectionFactory(REDIS.getHost(), redisPort()); connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection); redis.afterPropertiesSet();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        repository = new RedisInventoryReservationRepository(redis, new ObjectMapper().registerModule(new JavaTimeModule()), "test:inventory", "consumer", 10000, 20000, 10000);
        epoch = UUID.randomUUID();
        repository.bootstrap(new PipelineSnapshot(epoch, 0, "0-0", List.of(new InventorySeed(inventory, hub, product, 2, false)), List.of()));
    }
    @AfterEach void close() { connection.destroy(); }
    ReservationRequest decrease(String key, int quantity) {
        var now = LocalDateTime.now();
        return new ReservationRequest(UUID.randomUUID(), key, InventoryOperationType.DECREASE, null,
                UUID.nameUUIDFromBytes(key.getBytes()), hub, product, quantity, UUID.randomUUID(), now, now, null, now, null);
    }
    ReservationRequest restore(String key, UUID original) {
        var now = LocalDateTime.now();
        return new ReservationRequest(UUID.randomUUID(), key, InventoryOperationType.RESTORE, original, null, null, null, 0, null, now, now, null, now, null);
    }
    @Test void decreasesRejectsAndReplaysWithoutAppending() {
        var first = repository.execute(decrease("one", 2));
        assertThat(first.remainingQuantity()).isZero();
        var rejected = repository.execute(decrease("two", 1));
        assertThat(rejected.rejectionReason()).isEqualTo(InventoryRejectionReason.INSUFFICIENT_STOCK);
        assertThat(repository.execute(decrease("one", 2))).isEqualTo(first);
        assertThat(repository.state().revision()).isEqualTo(2);
        assertThat(repository.unconfirmed()).extracting(e -> e.event().sequence()).containsExactly(1L, 2L);
        assertThatThrownBy(() -> repository.execute(decrease("one", 1))).isInstanceOf(InventoryPipelineException.class)
                .extracting(e -> ((InventoryPipelineException)e).reason()).isEqualTo(InventoryPipelineException.Reason.IDEMPOTENCY_CONFLICT);
    }
    @Test void restoresBeforeDbAndAllowsOnlyOneOriginal() {
        var first = repository.execute(decrease("one", 2));
        var restored = repository.execute(restore("restore", first.operationId()));
        assertThat(restored.remainingQuantity()).isEqualTo(2);
        assertThat(repository.execute(restore("restore", first.operationId()))).isEqualTo(restored);
        assertThatThrownBy(() -> repository.execute(restore("other", first.operationId())))
                .extracting(e -> ((InventoryPipelineException)e).reason()).isEqualTo(InventoryPipelineException.Reason.ALREADY_RESTORED);
    }
    @Test void readsOwnPendingBeforeNewAndOnlyConfirmedPrefixIsRemoved() {
        repository.execute(decrease("one", 1));
        var pending = repository.read(1);
        repository.execute(decrease("two", 1));
        assertThat(repository.read(20)).isEqualTo(pending);
        var first = pending.getFirst();
        repository.confirm(epoch, first.event().sequence(), first.streamId());
        assertThat(repository.state().confirmedSequence()).isEqualTo(1);
        assertThat(repository.read(20)).extracting(e -> e.event().sequence()).containsExactly(2L);
    }
    @Test void idleReaderReceivesTheNextEventWithoutBlockingTheProducerConnection() throws Exception {
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var waiting = executor.submit(() -> repository.read(500));
            org.awaitility.Awaitility.await().pollInterval(java.time.Duration.ofMillis(5))
                    .atMost(java.time.Duration.ofSeconds(2)).until(() ->
                            redis.execute((org.springframework.data.redis.core.RedisCallback<Boolean>) c ->
                                    c.serverCommands().getClientList().stream().anyMatch(client ->
                                            "xreadgroup".equals(client.getLastCommand())
                                                    && client.getFlags().contains("b"))));

            var accepted = repository.execute(decrease("wake-reader", 1));

            assertThat(waiting.get(2, java.util.concurrent.TimeUnit.SECONDS))
                    .extracting(StreamEntry::event).containsExactly(accepted);
            assertThat(repository.quantities(List.of(inventory))).containsEntry(inventory, 1);
        } finally { executor.shutdownNow(); }
    }
    @Test void keyLossAndWrongTypeFailClosed() {
        redis.opsForHash().delete("test:inventory:" + epoch + ":stocks", inventory.toString());
        assertThatThrownBy(() -> repository.execute(decrease("one", 1))).isInstanceOf(InventoryPipelineException.class);
        assertThatThrownBy(() -> repository.loadInventory(new InventorySeed(inventory, hub, product, 99, false))).isInstanceOf(InventoryPipelineException.class);
        assertThat(repository.state().revision()).isZero();
    }
    @Test void backpressureRejectsDecreaseButLetsRestoreAndReplayThrough() {
        repository = new RedisInventoryReservationRepository(redis, new ObjectMapper().registerModule(new JavaTimeModule()), "test:inventory", "consumer", 1, 2, 10000);
        var first = repository.execute(decrease("one", 1));
        assertThatThrownBy(() -> repository.execute(decrease("two", 1))).isInstanceOf(InventoryPipelineException.class);
        assertThat(repository.execute(decrease("one", 1))).isEqualTo(first);
        assertThat(repository.execute(restore("restore", first.operationId())).remainingQuantity()).isEqualTo(2);
        assertThatThrownBy(() -> repository.execute(decrease("three", 1))).isInstanceOf(InventoryPipelineException.class);
    }
    @Test void missingInventoryRejectionRemainsStickyAfterRegistration() {
        UUID absentProduct = UUID.randomUUID();
        var request = decrease("missing", 1);
        request = new ReservationRequest(request.operationId(), request.idempotencyKey(), request.operationType(), null,
                request.orderId(), hub, absentProduct, 1, request.actorId(), request.occurredAt(), request.createdAt(), request.createdBy(), request.updatedAt(), request.updatedBy());
        var missing = repository.execute(request);
        assertThat(missing.inventoryId()).isNull();
        repository.loadInventory(new InventorySeed(UUID.randomUUID(), hub, absentProduct, 10, false));
        assertThat(repository.execute(request)).isEqualTo(missing);
    }
    @Test void wrongStreamTypeIsDetectedBeforeQuantityMutation() {
        String stream = "test:inventory:" + epoch + ":stream";
        redis.delete(stream); redis.opsForValue().set(stream, "wrong");
        assertThatThrownBy(() -> repository.execute(decrease("one", 1))).isInstanceOf(InventoryPipelineException.class);
        assertThat(redis.opsForHash().get("test:inventory:" + epoch + ":stocks", inventory.toString())).isEqualTo("2");
    }
    @Test void dirtyAndEpochLossRejectEvenCompletedReplay() {
        repository.execute(decrease("one", 1));
        redis.opsForHash().put("test:inventory:engine", "status", "DIRTY");
        assertThatThrownBy(() -> repository.execute(decrease("one", 1))).isInstanceOf(InventoryPipelineException.class);
        redis.delete("test:inventory:engine");
        assertThat(repository.state().status()).isEqualTo(PipelineState.Status.MISSING);
        assertThatThrownBy(() -> repository.execute(decrease("one", 1))).isInstanceOf(InventoryPipelineException.class);
    }
    @Test void rebuildResequencesCompleteEventsAndIgnoresPartialMutation() {
        var first = repository.execute(decrease("one", 1));
        var entries = repository.unconfirmed();
        redis.opsForHash().put("test:inventory:" + epoch + ":stocks", inventory.toString(), "0");
        redis.opsForHash().put("test:inventory:engine", "status", "DIRTY");
        UUID recovered = UUID.randomUUID();
        repository.rebuild(new PipelineSnapshot(epoch, 0, "0-0", List.of(new InventorySeed(inventory, hub, product, 2, false)), List.of()), entries, recovered);
        assertThat(repository.quantities(List.of(inventory))).containsEntry(inventory, 1);
        assertThat(repository.execute(decrease("one", 1)).operationId()).isEqualTo(first.operationId());
        assertThat(repository.unconfirmed()).extracting(e -> e.event().epoch()).containsExactly(recovered);
        assertThat(repository.execute(restore("restore", first.operationId())).remainingQuantity()).isEqualTo(2);
    }

    @Test void runtimeErrorAfterMutationLeavesDirtyUntilSnapshotRebuild() {
        redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) c -> c.execute("ACL", "SETUSER".getBytes(), "reservation".getBytes(), "on".getBytes(), ">test-password".getBytes(), "~test:inventory*".getBytes(), "+@all".getBytes(), "-xadd".getBytes()));
        var configuration = new org.springframework.data.redis.connection.RedisStandaloneConfiguration(REDIS.getHost(), redisPort());
        configuration.setUsername("reservation"); configuration.setPassword("test-password");
        var limitedConnection = new LettuceConnectionFactory(configuration); limitedConnection.afterPropertiesSet();
        try {
            var limited = new StringRedisTemplate(limitedConnection); limited.afterPropertiesSet();
            var limitedRepository = new RedisInventoryReservationRepository(limited, new ObjectMapper().registerModule(new JavaTimeModule()), "test:inventory", "consumer", 10000, 20000, 10000);
            assertThatThrownBy(() -> limitedRepository.execute(decrease("one", 1))).isInstanceOf(InventoryPipelineException.class);
            assertThat(repository.state().status()).isEqualTo(PipelineState.Status.DIRTY);
            assertThat(redis.opsForHash().get("test:inventory:" + epoch + ":stocks", inventory.toString())).isEqualTo("1");
            assertThat(repository.unconfirmed()).isEmpty();
            assertThatThrownBy(() -> repository.execute(decrease("one", 1))).isInstanceOf(InventoryPipelineException.class);
            repository.rebuild(new PipelineSnapshot(epoch, 0, "0-0", List.of(new InventorySeed(inventory, hub, product, 2, false)), List.of()), List.of(), UUID.randomUUID());
            assertThat(repository.quantities(List.of(inventory))).containsEntry(inventory, 2);
        } finally { limitedConnection.destroy(); }
    }
    @Test void aofRestartRetainsQuantityIdempotencyAndPending() throws Exception {
        var first=repository.execute(decrease("one",1));
        var pending=repository.read(10);
        REDIS.getDockerClient().restartContainerCmd(REDIS.getContainerId()).withTimeout(1).exec();
        connection.destroy();
        connection=new LettuceConnectionFactory(REDIS.getHost(),redisPort()); connection.afterPropertiesSet();
        redis=new StringRedisTemplate(connection); redis.afterPropertiesSet();
        repository=new RedisInventoryReservationRepository(redis,new ObjectMapper().registerModule(new JavaTimeModule()),"test:inventory","consumer",10000,20000,10000);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).ignoreExceptions().untilAsserted(() -> assertThat(redis.getConnectionFactory().getConnection().ping()).isEqualTo("PONG"));
        assertThat(repository.quantities(List.of(inventory))).containsEntry(inventory,1);
        assertThat(repository.execute(decrease("one",1))).isEqualTo(first);
        assertThat(repository.read(10)).isEqualTo(pending);
    }
    @Test void restoreOverflowIsRejectedAfterReplayAndRestoredChecks() {
        var first=repository.execute(decrease("one",1));
        var second=repository.execute(decrease("two",1));
        var entries=repository.unconfirmed();
        var historical = List.of(first, second);
        repository.pause();
        repository.rebuild(new PipelineSnapshot(epoch,2,entries.getLast().streamId(),List.of(new InventorySeed(inventory,hub,product,Integer.MAX_VALUE,false)),historical),List.of(),UUID.randomUUID());
        assertThatThrownBy(() -> repository.execute(restore("restore",first.operationId())))
                .extracting(e -> ((InventoryPipelineException)e).reason()).isEqualTo(InventoryPipelineException.Reason.QUANTITY_LIMIT);
        assertThat(repository.state().revision()).isZero();
    }
    @Test void tombstoneSurvivesRebuildEvenWhenPgDeleteHasNotCommitted() {
        repository.tombstoneProduct(product);
        repository.pause();
        repository.rebuild(new PipelineSnapshot(epoch,0,"0-0",List.of(new InventorySeed(inventory,hub,product,2,false)),List.of()),List.of(),UUID.randomUUID());
        assertThat(repository.isProductDeleted(product)).isTrue();
        assertThat(repository.execute(decrease("deleted",1)).rejectionReason()).isEqualTo(InventoryRejectionReason.INVENTORY_NOT_FOUND);
    }

    @Test void hardLimitBlocksRestoreAndAgeLimitOnlyBlocksNewDecrease() {
        repository=new RedisInventoryReservationRepository(redis,new ObjectMapper().registerModule(new JavaTimeModule()),"test:inventory","consumer",10000,2,1);
        var first=repository.execute(decrease("one",1));
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(1)).until(() -> System.currentTimeMillis()-repository.state().oldestUnconfirmedAtMillis()>2);
        assertThatThrownBy(() -> repository.execute(decrease("aged",1))).isInstanceOf(InventoryPipelineException.class);
        assertThat(repository.execute(restore("restore",first.operationId())).remainingQuantity()).isEqualTo(2);
        var next=decrease("next",1);
        repository=new RedisInventoryReservationRepository(redis,new ObjectMapper().registerModule(new JavaTimeModule()),"test:inventory","consumer",10000,2,10000);
        var second=repository.execute(next);
        assertThatThrownBy(() -> repository.execute(restore("hard",second.operationId())))
                .extracting(e -> ((InventoryPipelineException)e).reason()).isEqualTo(InventoryPipelineException.Reason.UNAVAILABLE);
    }
    @Test void bootstrapsHistoricalIdempotencyKeysAndOriginalRestoreMarkers() {
        var first=repository.execute(decrease("one",1));
        var restored=repository.execute(restore("restore",first.operationId()));
        var other=new RedisInventoryReservationRepository(redis,new ObjectMapper().registerModule(new JavaTimeModule()),"history:inventory","consumer",10000,20000,10000);
        other.bootstrap(new PipelineSnapshot(UUID.randomUUID(),0,"0-0",List.of(new InventorySeed(inventory,hub,product,2,false)),List.of(first,restored)));
        assertThat(other.execute(decrease("one",1))).isEqualTo(first);
        assertThat(other.execute(restore("restore",first.operationId()))).isEqualTo(restored);
        assertThatThrownBy(() -> other.execute(restore("other",first.operationId())))
                .extracting(e -> ((InventoryPipelineException)e).reason()).isEqualTo(InventoryPipelineException.Reason.ALREADY_RESTORED);
        assertThat(other.unconfirmed()).isEmpty();
    }

    @Test void coldLoadRefusesAnyLostEpochKeyBeforeAddingStock() {
        redis.delete("test:inventory:"+epoch+":operations");
        var another=new InventorySeed(UUID.randomUUID(),hub,UUID.randomUUID(),7,false);
        assertThatThrownBy(() -> repository.loadInventory(another)).isInstanceOf(InventoryPipelineException.class);
        assertThat(redis.opsForHash().hasKey("test:inventory:"+epoch+":stocks",another.inventoryId().toString())).isFalse();
    }

    @Test void everyValidIdempotencyKeyIncludingMetadataLikeTextIsUsable() {
        var result=repository.execute(decrease("_meta",1));
        assertThat(result.remainingQuantity()).isEqualTo(1);
        assertThat(repository.execute(decrease("_meta",1))).isEqualTo(result);
    }

    @Test void maximumLengthUnicodeKeyUsesJavaCharacterContract() {
        var request=decrease("한".repeat(120),1);
        assertThat(repository.execute(request).remainingQuantity()).isEqualTo(1);
        assertThat(repository.execute(request).idempotencyKey()).isEqualTo(request.idempotencyKey());
    }
    @Test void corruptFractionalStockIsRejectedBeforeMutation() {
        redis.opsForHash().put("test:inventory:"+epoch+":stocks",inventory.toString(),"1.5");
        assertThatThrownBy(() -> repository.execute(decrease("one",1))).isInstanceOf(InventoryPipelineException.class);
        assertThat(redis.opsForHash().get("test:inventory:"+epoch+":stocks",inventory.toString())).isEqualTo("1.5");
        assertThat(repository.state().revision()).isZero();
    }
    @Test void corruptRevisionOrCheckpointIsRejectedBeforeMutation() {
        for(String[] values:List.of(new String[]{"0.5","0"},new String[]{"0","-1"},new String[]{"9007199254740992","0"})) {
            redis.opsForHash().put("test:inventory:engine","revision",values[0]);
            redis.opsForHash().put("test:inventory:engine","confirmed",values[1]);
            assertThatThrownBy(() -> repository.execute(decrease("one",1))).isInstanceOf(InventoryPipelineException.class);
            assertThat(redis.opsForHash().get("test:inventory:"+epoch+":stocks",inventory.toString())).isEqualTo("2");
        }
    }

    @Test void lostKnownStockAlsoBlocksCompletedReplay() {
        repository.execute(decrease("one",1));
        redis.opsForHash().delete("test:inventory:"+epoch+":stocks",inventory.toString());
        assertThatThrownBy(() -> repository.execute(decrease("one",1))).isInstanceOf(InventoryPipelineException.class);
    }


    @Test void sequenceCannotExceedRedisJsonExactDecimalPrecision() {
        redis.opsForHash().put("test:inventory:engine","revision","100000000000000");
        redis.opsForHash().put("test:inventory:engine","confirmed","100000000000000");
        assertThatThrownBy(() -> repository.execute(decrease("precision",1))).isInstanceOf(InventoryPipelineException.class);
        assertThat(repository.quantities(List.of(inventory)).get(inventory)).isEqualTo(2);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void recoveryRefusesAMissingAcceptedStreamSuffix(boolean entireKey) {
        repository.execute(decrease("accepted",1));
        var last=repository.unconfirmed().getLast();
        String stream="test:inventory:"+epoch+":stream";
        if(entireKey) redis.delete(stream);
        else redis.opsForStream().delete(stream,last.streamId());
        assertThatThrownBy(() -> repository.rebuild(new PipelineSnapshot(epoch,0,"0-0",
                List.of(new InventorySeed(inventory,hub,product,2,false)),List.of()),repository.unconfirmed(),UUID.randomUUID()))
                .isInstanceOf(InventoryPipelineException.class);
        assertThat(repository.state().epoch()).isEqualTo(epoch);
    }
    @Test void quantitySnapshotIsReadAtomicallyBeforeAConcurrentDirtyMutation() {
        var armed=new java.util.concurrent.atomic.AtomicBoolean(false);
        StringRedisTemplate interleaved=new StringRedisTemplate(connection) {
            @Override public <T> T execute(org.springframework.data.redis.core.script.RedisScript<T> script,List<String> keys,Object... args) {
                T result=redis.execute(script,keys,args);
                if(armed.compareAndSet(true,false)) {
                    // Another real Redis mutation fails just after this Lua has completed.
                    redis.opsForHash().put("test:inventory:"+epoch+":stocks",inventory.toString(),"0");
                    redis.opsForHash().put("test:inventory:engine","status","DIRTY");
                }
                return result;
            }
        };
        var reader=new RedisInventoryReservationRepository(interleaved,new ObjectMapper().registerModule(new JavaTimeModule()),"test:inventory","consumer",10000,20000,10000);
        reader.bootstrap(new PipelineSnapshot(epoch,0,"0-0",List.of(),List.of()));
        armed.set(true);
        assertThat(reader.quantities(List.of(inventory)).get(inventory)).isEqualTo(2);
        assertThat(repository.state().status()).isEqualTo(PipelineState.Status.DIRTY);
    }
}
