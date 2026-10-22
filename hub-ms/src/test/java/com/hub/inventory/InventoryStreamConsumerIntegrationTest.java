package com.hub.inventory;

import com.common.auth.AuthContract;
import com.hub.inventory.application.service.InventoryPipelineService;
import com.hub.inventory.application.service.InventoryStreamConsumer;
import com.hub.inventory.domain.client.ProductClient;
import com.hub.inventory.domain.stream.InventoryReservationRepository;
import com.hub.support.TestPostgresConnectConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=false",
        "spring.jpa.show-sql=false", "inventory.stock.mode=redis-streams",
        "inventory.streams.consumer-enabled=true"
})
@AutoConfigureMockMvc
@Import(TestPostgresConnectConfig.class)
class InventoryStreamConsumerIntegrationTest {
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--appendonly", "yes", "--appendfsync", "always",
                    "--maxmemory-policy", "noeviction");
    static { REDIS.start(); }
    @AfterAll static void stopRedis() { REDIS.stop(); }
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryStreamConsumer consumer;
    @Autowired InventoryReservationRepository reservations;
    @Autowired InventoryPipelineService pipeline;
    @MockitoBean ProductClient products;

    @AfterEach void stopConsumer() { consumer.stop(); }

    @Test
    void automaticallyPersistsRequestsAndResumesPendingWorkAfterRestart() throws Exception {
        UUID hub = UUID.randomUUID(), product = UUID.randomUUID(), inventory = UUID.randomUUID();
        jdbc.update("INSERT INTO hub.p_hubs(id,name,address,created_at) VALUES (?,?,?,CURRENT_TIMESTAMP)",
                hub, "consumer-" + hub, "test");
        jdbc.update("INSERT INTO hub.p_inventories(id,hub_id,product_id,quantity,created_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)",
                inventory, hub, product, 100);

        decrease(hub, product, 30);
        awaitStored(inventory, 70, 1);

        // 종료된 Consumer는 새 요청을 저장하지 않는다. 이벤트는 Stream에 남는다.
        consumer.stop();
        decrease(hub, product, 20);
        assertThat(quantity(inventory)).isEqualTo(70);
        assertThat(reservations.state().backlog()).isEqualTo(1);

        consumer.start();
        consumer.start();
        awaitStored(inventory, 50, 2);
        assertThat(pipeline.verifyDrained()).isTrue();
    }

    private void decrease(UUID hub, UUID product, int quantity) throws Exception {
        UUID order = UUID.randomUUID();
        mvc.perform(post("/internal/v1/inventories/decrease")
                        .header("Idempotency-Key", "worker-" + order)
                        .header(AuthContract.HEADER_USER_ID, AuthContract.SYSTEM_USER_ID)
                        .header(AuthContract.HEADER_USER_ROLE, "MASTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","hubId":"%s","productId":"%s","quantity":%d}
                                """.formatted(order, hub, product, quantity)))
                .andExpect(status().isOk());
    }

    private void awaitStored(UUID inventory, int remaining, long operations) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(quantity(inventory)).isEqualTo(remaining);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hub.p_inventory_operations WHERE inventory_id=?",
                    Long.class, inventory)).isEqualTo(operations);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hub.p_inventory_histories WHERE inventory_id=?",
                    Long.class, inventory)).isEqualTo(operations);
            assertThat(reservations.state().backlog()).isZero();
        });
    }

    private int quantity(UUID inventory) {
        return jdbc.queryForObject("SELECT quantity FROM hub.p_inventories WHERE id=?", Integer.class, inventory);
    }
}
