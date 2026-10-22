package com.hub.inventory;

import com.hub.support.TestPostgresConnectConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import com.hub.inventory.application.service.*;
import com.hub.inventory.domain.stream.*;
import com.hub.inventory.infrastructure.redis.RedisInventoryReservationRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Redis 응답과 PostgreSQL 반영 시점의 차이를 실제 두 저장소로 검증한다. */
@SpringBootTest(properties = {
        "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=false",
        "spring.jpa.show-sql=false", "inventory.stock.mode=redis-streams",
        "inventory.streams.consumer-enabled=false"
})
@AutoConfigureMockMvc
@Import(TestPostgresConnectConfig.class)
class InventoryRedisStreamsIntegrationTest {
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--appendonly", "yes", "--appendfsync", "always",
                    "--maxmemory-policy", "noeviction");
    static { REDIS.start(); }
    @org.junit.jupiter.api.AfterAll static void stopRedis() { REDIS.stop(); }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryService inventoryService;
    @Autowired org.testcontainers.containers.PostgreSQLContainer<?> postgres;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.hub.inventory.domain.client.ProductClient products;
    @Autowired InventoryStreamConsumer consumer;
    @Autowired InventoryPipelineService pipeline;
    @Autowired StringRedisTemplate redis;
    @MockitoSpyBean RedisInventoryReservationRepository reservations;
    @MockitoSpyBean com.hub.inventory.infrastructure.persistence.PostgresInventoryBatchRepository batches;

    @AfterEach void drain() {
        for(int i=0;i<100 && reservations.state().backlog()>0;i++) consumer.consumeOnce();
        assertThat(reservations.state().backlog()).as(consumer.lastFailure().orElse("drained")).isZero();
        assertThat(pipeline.verifyDrained()).isTrue();
    }
    record Fixture(UUID hub, UUID product, UUID inventory) { }
    Fixture fixture(int quantity) {
        Fixture f=new Fixture(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        jdbc.update("INSERT INTO hub.p_hubs(id,name,address,created_at) VALUES(?,?,?,CURRENT_TIMESTAMP)",f.hub(),"redis-"+f.hub(),"test");
        jdbc.update("INSERT INTO hub.p_inventories(id,hub_id,product_id,quantity,created_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP)",f.inventory(),f.hub(),f.product(),quantity);
        return f;
    }
    org.springframework.test.web.servlet.MvcResult decrease(Fixture f,UUID order,String key,int quantity) throws Exception {
        return mvc.perform(post("/internal/v1/inventories/decrease").header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"%s\",\"hubId\":\"%s\",\"productId\":\"%s\",\"quantity\":%d}".formatted(order,f.hub(),f.product(),quantity))).andReturn();
    }
    int dbQuantity(Fixture f) { return jdbc.queryForObject("SELECT quantity FROM hub.p_inventories WHERE id=?",Integer.class,f.inventory()); }
    long count(String table,Fixture f) { return jdbc.queryForObject("SELECT COUNT(*) FROM hub."+table+" WHERE inventory_id=?",Long.class,f.inventory()); }


    @Test
    void acceptsDecreaseAndImmediateRestoreBeforePostgresPersistence() throws Exception {
        UUID hub = UUID.randomUUID(), product = UUID.randomUUID(), inventory = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        jdbc.update("INSERT INTO hub.p_hubs(id,name,address,created_at) VALUES (?,?,?,CURRENT_TIMESTAMP)",
                hub, "redis-" + hub, "test");
        jdbc.update("INSERT INTO hub.p_inventories(id,hub_id,product_id,quantity,created_at) VALUES (?,?,?,100,CURRENT_TIMESTAMP)",
                inventory, hub, product);
        String response = mvc.perform(post("/internal/v1/inventories/decrease")
                        .header("Idempotency-Key", "d-" + order)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","hubId":"%s","productId":"%s","quantity":30}
                                """.formatted(order, hub, product)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String operation = JsonPath.read(response, "$.operationId");
        assertThat((Integer) JsonPath.read(response, "$.remainingQuantity")).isEqualTo(70);
        // Consumer가 꺼져 있으므로 API 성공 시점에도 PostgreSQL은 아직 이전 상태다.
        assertThat(jdbc.queryForObject("SELECT quantity FROM hub.p_inventories WHERE id=?", Integer.class, inventory)).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hub.p_inventory_operations WHERE id=?", Long.class,
                UUID.fromString(operation))).isZero();
        String restored = mvc.perform(post("/internal/v1/inventories/restore")
                        .header("Idempotency-Key", "r-" + order)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalOperationId\":\"" + operation + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(restored, "$.remainingQuantity")).isEqualTo(100);
    }

    @Test void concurrentThousandDistinctRequestsMaintainExactCounts() throws Exception {
        Fixture f=fixture(100);
        ExecutorService workers=Executors.newFixedThreadPool(20);
        try {
            List<Callable<Integer>> calls=new ArrayList<>();
            for(int i=0;i<1000;i++) calls.add(() -> decrease(f,UUID.randomUUID(),"load-"+UUID.randomUUID(),1).getResponse().getStatus());
            List<Integer> statuses=new ArrayList<>();
            for(var future:workers.invokeAll(calls)) statuses.add(future.get(30,TimeUnit.SECONDS));
            assertThat(statuses.stream().filter(v->v==200).count()).isEqualTo(100);
            assertThat(statuses.stream().filter(v->v==409).count()).isEqualTo(900);
            assertThat(reservations.quantities(List.of(f.inventory())).get(f.inventory())).isZero();
            assertThat(dbQuantity(f)).isEqualTo(100);
            drain();
            assertThat(dbQuantity(f)).isZero();
            assertThat(count("p_inventory_operations",f)).isEqualTo(1000);
            assertThat(count("p_inventory_histories",f)).isEqualTo(100);
        } finally { workers.shutdownNow(); }
    }
    @Test void concurrentReplayAndDifferentPayloadAreHandledBeforeStockMutation() throws Exception {
        Fixture f=fixture(100); UUID order=UUID.randomUUID(); String key="same-"+order;
        ExecutorService workers=Executors.newFixedThreadPool(20);
        try {
            List<Callable<String>> calls=new ArrayList<>();
            for(int i=0;i<100;i++) calls.add(() -> {
                var response=decrease(f,order,key,30).getResponse();
                assertThat(response.getStatus()).isEqualTo(200);
                return response.getContentAsString();
            });
            Set<String> ids=new HashSet<>();
            for(var future:workers.invokeAll(calls)) ids.add(JsonPath.read(future.get(),"$.operationId"));
            assertThat(ids).hasSize(1);
            assertThat(decrease(f,order,key,50).getResponse().getStatus()).isEqualTo(409);
            drain(); assertThat(dbQuantity(f)).isEqualTo(70);
            assertThat(count("p_inventory_operations",f)).isEqualTo(1);
            assertThat(count("p_inventory_histories",f)).isEqualTo(1);
        } finally { workers.shutdownNow(); }
    }
    @Test void committedBatchIsReplayedAfterAckFailureWithoutApplyingAgain() throws Exception {
        Fixture f=fixture(100);
        decrease(f,UUID.randomUUID(),"ack-"+UUID.randomUUID(),30);
        doThrow(new InventoryPipelineException(InventoryPipelineException.Reason.UNAVAILABLE))
                .when(reservations).confirm(any(),anyLong(),anyString());
        try {
            assertThat(consumer.consumeOnce()).isZero();
            assertThat(dbQuantity(f)).isEqualTo(70);
            assertThat(reservations.state().backlog()).isPositive();
        } finally { reset(reservations); }
        drain();
        assertThat(dbQuantity(f)).isEqualTo(70);
        assertThat(count("p_inventory_operations",f)).isEqualTo(1);
        assertThat(count("p_inventory_histories",f)).isEqualTo(1);
    }
    @Test void poisonHistoryFailureKeepsEventUntilCauseIsFixed() throws Exception {
        Fixture f=fixture(100);
        jdbc.execute("ALTER TABLE hub.p_inventory_histories ADD CONSTRAINT test_stream_poison CHECK(inventory_id <> '"+f.inventory()+"')");
        try {
            assertThat(decrease(f,UUID.randomUUID(),"poison-"+UUID.randomUUID(),30).getResponse().getStatus()).isEqualTo(200);
            assertThat(consumer.consumeOnce()).isZero();
            assertThat(dbQuantity(f)).isEqualTo(100);
            assertThat(count("p_inventory_operations",f)).isZero();
            assertThat(reservations.state().backlog()).isPositive();
        } finally { jdbc.execute("ALTER TABLE hub.p_inventory_histories DROP CONSTRAINT test_stream_poison"); }
        drain(); assertThat(dbQuantity(f)).isEqualTo(70);
    }
    @Test void dirtyPartialChangeIsDiscardedByCheckpointAndCompleteEventRecovery() throws Exception {
        Fixture f=fixture(100);
        var accepted=decrease(f,UUID.randomUUID(),"recover-"+UUID.randomUUID(),30);
        assertThat(accepted.getResponse().getStatus()).isEqualTo(200);
        var old=reservations.state();
        String stocks="inventory:streams:"+old.epoch()+":stocks";
        redis.opsForHash().increment(stocks,f.inventory().toString(),-5);
        redis.opsForHash().put("inventory:streams:engine","status","DIRTY");
        assertThat(decrease(f,UUID.randomUUID(),"blocked-"+UUID.randomUUID(),1).getResponse().getStatus()).isEqualTo(503);
        pipeline.recover();
        assertThat(reservations.state().epoch()).isNotEqualTo(old.epoch());
        assertThat(reservations.quantities(List.of(f.inventory())).get(f.inventory())).isEqualTo(70);
        drain(); assertThat(dbQuantity(f)).isEqualTo(70);
    }
    @Test void lostEngineCannotAutomaticallyReloadTheOlderDatabase() throws Exception {
        Fixture f=fixture(100);
        assertThat(decrease(f,UUID.randomUUID(),"loss-"+UUID.randomUUID(),30).getResponse().getStatus()).isEqualTo(200);
        String engine="inventory:streams:engine";
        var backup=redis.opsForHash().entries(engine);
        redis.delete(engine);
        try {
            assertThat(decrease(f,UUID.randomUUID(),"blocked-"+UUID.randomUUID(),1).getResponse().getStatus()).isEqualTo(503);
            assertThat(dbQuantity(f)).isEqualTo(100);
        } finally { redis.opsForHash().putAll(engine,backup); }
    }

    @Test void recoveryCanResumeWhenDatabaseEpochSwitchFailsAfterRedisRebuild() throws Exception {
        Fixture f=fixture(100);
        assertThat(decrease(f,UUID.randomUUID(),"handoff-"+UUID.randomUUID(),30).getResponse().getStatus()).isEqualTo(200);
        UUID previous=reservations.state().epoch();
        com.hub.inventory.infrastructure.persistence.PostgresInventoryBatchRepository target =
                org.springframework.test.util.AopTestUtils.getUltimateTargetObject(batches);
        doThrow(new IllegalStateException("simulated checkpoint update outage")).when(target).switchEpoch(any(),any());
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(pipeline::recover).isInstanceOf(RuntimeException.class);
        } finally { reset(target); }
        assertThat(reservations.state().epoch()).isNotEqualTo(previous);
        // Simulate the startup runner after a process restart at this exact boundary.
        pipeline.run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));
        assertThat(decrease(f,UUID.randomUUID(),"handoff-after-"+UUID.randomUUID(),1).getResponse().getStatus()).isEqualTo(200);
        drain(); assertThat(dbQuantity(f)).isEqualTo(69);
    }

    @Test void preservesRequestAuditorAndReturnsRedisQuantityBeforeDbCatchup() throws Exception {
        Fixture f=fixture(100); UUID actor=UUID.randomUUID(),order=UUID.randomUUID();
        mvc.perform(post("/internal/v1/inventories/decrease").header("Idempotency-Key","audit-"+order)
                .header("X-User-Id",actor).header("X-User-Role","MASTER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"%s\",\"hubId\":\"%s\",\"productId\":\"%s\",\"quantity\":30}".formatted(order,f.hub(),f.product())))
                .andExpect(status().isOk());
        assertThat(inventoryService.getInventories(f.hub(),f.product()).getFirst().quantity()).isEqualTo(70);
        assertThat(dbQuantity(f)).isEqualTo(100);
        drain();
        assertThat(jdbc.queryForObject("SELECT created_by FROM hub.p_inventory_operations WHERE order_id=?",UUID.class,order)).isEqualTo(actor);
        assertThat(jdbc.queryForObject("SELECT created_by FROM hub.p_inventory_histories WHERE order_id=?",UUID.class,order)).isEqualTo(actor);
        assertThat(jdbc.queryForObject("SELECT updated_by FROM hub.p_inventories WHERE id=?",UUID.class,f.inventory())).isEqualTo(actor);
    }
    @Test void productDeletionBlocksNewReservationsButPersistsPreviouslyAcceptedChange() throws Exception {
        Fixture f=fixture(100); UUID actor=UUID.randomUUID();
        String body=decrease(f,UUID.randomUUID(),"before-delete-"+UUID.randomUUID(),30).getResponse().getContentAsString();
        String original=JsonPath.read(body,"$.operationId");
        inventoryService.deleteInventoriesByProductId(f.product(),actor);
        assertThat(decrease(f,UUID.randomUUID(),"after-delete-"+UUID.randomUUID(),1).getResponse().getStatus()).isEqualTo(404);
        mvc.perform(post("/internal/v1/inventories/restore").header("Idempotency-Key","deleted-restore-"+UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"originalOperationId\":\""+original+"\"}"))
                .andExpect(status().isNotFound());
        assertThat(inventoryService.getInventories(f.hub(),f.product())).isEmpty();
        drain(); assertThat(dbQuantity(f)).isEqualTo(70);
        assertThat(jdbc.queryForObject("SELECT deleted_by FROM hub.p_inventories WHERE id=?",UUID.class,f.inventory())).isEqualTo(actor);
        assertThat(jdbc.queryForObject("SELECT updated_by FROM hub.p_inventories WHERE id=?",UUID.class,f.inventory())).isEqualTo(actor);
    }
    @Test void registrationAfterMissingDoesNotChangeThePreviousRejection() throws Exception {
        UUID hub=UUID.randomUUID(),product=UUID.randomUUID(),actor=UUID.randomUUID(),order=UUID.randomUUID();
        jdbc.update("INSERT INTO hub.p_hubs(id,name,address,created_at) VALUES(?,?,?,CURRENT_TIMESTAMP)",hub,"registration-"+hub,"test");
        Fixture missing=new Fixture(hub,product,UUID.randomUUID()); String key="missing-"+order;
        assertThat(decrease(missing,order,key,1).getResponse().getStatus()).isEqualTo(404);
        when(products.findActiveProduct(product)).thenReturn(Optional.of(new com.hub.inventory.domain.client.ProductInfo(product,UUID.randomUUID(),hub,actor)));
        var created=inventoryService.createInventory(new com.hub.inventory.application.dto.CreateInventoryCommand(hub,product,10,actor,true));
        assertThat(created.quantity()).isEqualTo(10);
        assertThat(decrease(missing,order,key,1).getResponse().getStatus()).isEqualTo(404);
        Fixture actual=new Fixture(hub,product,created.id());
        assertThat(decrease(actual,UUID.randomUUID(),"registered-"+UUID.randomUUID(),1).getResponse().getStatus()).isEqualTo(200);
        drain(); assertThat(dbQuantity(actual)).isEqualTo(9);
    }
    @Test void cachedInventoryAcceptsRequestsWhileDatabaseIsPausedAndDrainsAfterResume() throws Exception {
        Fixture f=fixture(100);
        assertThat(decrease(f,UUID.randomUUID(),"pause-before-"+UUID.randomUUID(),30).getResponse().getStatus()).isEqualTo(200);
        var docker=org.testcontainers.DockerClientFactory.instance().client();
        docker.pauseContainerCmd(postgres.getContainerId()).exec();
        CompletableFuture<Integer> consuming=CompletableFuture.supplyAsync(consumer::consumeOnce);
        try {
            assertThat(decrease(f,UUID.randomUUID(),"pause-during-"+UUID.randomUUID(),1).getResponse().getStatus()).isEqualTo(200);
            assertThat(reservations.state().backlog()).isGreaterThanOrEqualTo(2);
        } finally { docker.unpauseContainerCmd(postgres.getContainerId()).exec(); }
        consuming.get(30,TimeUnit.SECONDS);
        drain(); assertThat(dbQuantity(f)).isEqualTo(69);
        assertThat(count("p_inventory_histories",f)).isEqualTo(2);
    }

    @Test void malformedEventDoesNotPreventCommittingItsValidPrefix() throws Exception {
        Fixture f=fixture(100);
        assertThat(decrease(f,UUID.randomUUID(),"prefix-first-"+UUID.randomUUID(),30).getResponse().getStatus()).isEqualTo(200);
        assertThat(decrease(f,UUID.randomUUID(),"prefix-second-"+UUID.randomUUID(),20).getResponse().getStatus()).isEqualTo(200);
        String stream="inventory:streams:"+reservations.state().epoch()+":stream";
        var records=redis.opsForStream().range(stream,org.springframework.data.domain.Range.unbounded());
        var first=records.getFirst(); var second=records.getLast();
        var malformed=org.springframework.data.redis.connection.stream.StreamRecords.newRecord()
                .in(stream).ofMap(Map.of("event","{broken-json")).withId(second.getId());
        // Recreate only this isolated test stream to inject corrupt bytes with the original IDs.
        redis.delete(stream); redis.opsForStream().add(first); redis.opsForStream().add(malformed);
        redis.opsForStream().createGroup(stream,org.springframework.data.redis.connection.stream.ReadOffset.from("0-0"),"inventory-persistence");
        assertThat(decrease(f,UUID.randomUUID(),"prefix-third-"+UUID.randomUUID(),1).getResponse().getStatus()).isEqualTo(200);
        var third=redis.opsForStream().range(stream,org.springframework.data.domain.Range.unbounded()).getLast();
        try {
            assertThat(consumer.consumeOnce()).isZero();
            assertThat(dbQuantity(f)).isEqualTo(70);
            assertThat(count("p_inventory_operations",f)).isEqualTo(1);
            assertThat(redis.opsForStream().pending(stream,"inventory-persistence").getTotalPendingMessages()).isEqualTo(2);
        } finally {
            // Restore the known original suffix in the isolated fixture; the bad event was never skipped by Consumer.
            redis.delete(stream); redis.opsForStream().add(second); redis.opsForStream().add(third);
            redis.opsForStream().createGroup(stream,org.springframework.data.redis.connection.stream.ReadOffset.from("0-0"),"inventory-persistence");
        }
        drain(); assertThat(dbQuantity(f)).isEqualTo(49);
    }
}
