package com.hub.inventory;

import com.hub.support.TestPostgresConnectConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=false",
        "spring.jpa.show-sql=true",
        "spring.jpa.properties.hibernate.format_sql=true"
})
@AutoConfigureMockMvc
@Import(TestPostgresConnectConfig.class)
public class InventoryStockIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID hubId;
    private UUID productId;
    private UUID inventoryId;

    @BeforeEach
    void setUp() {
        hubId = UUID.randomUUID();
        productId = UUID.randomUUID();
        inventoryId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO hub.p_hubs (
                    id, name, address, created_at
                )
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """,
                hubId,
                "재고 변경 테스트 허브 -"+ hubId,
                "테스트 주소"
        );

        jdbcTemplate.update("""
                INSERT INTO hub.p_inventories (
                    id, hub_id, product_id, quantity, created_at
                )
                VALUES (?, ?, ?, 100, CURRENT_TIMESTAMP)
                """,
                inventoryId,
                hubId,
                productId
        );
    }

    @Test
    @DisplayName("재고를 차감하고 작업 결과와 변경 이력을 저장한다")
    void decreaseStock() throws Exception {
        UUID orderId = UUID.randomUUID();
        String idempotencyKey = "order:" + orderId + ":decrease";

        String responseBody = mockMvc.perform(
                        post("/internal/v1/inventories/decrease")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "orderId": "%s",
                                      "hubId": "%s",
                                      "productId": "%s",
                                      "quantity": 30
                                    }
                                    """.formatted(orderId, hubId, productId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").isNotEmpty())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.inventoryId").value(inventoryId.toString()))
                .andExpect(jsonPath("$.changedQuantity").value(-30))
                .andExpect(jsonPath("$.remainingQuantity").value(70))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String operationIdValue = JsonPath.read(responseBody, "$.operationId");
        UUID operationId = UUID.fromString(operationIdValue);

        // 응답뿐 아니라 실제 저장된 재고 수량도 확인한다.
        Integer storedQuantity = jdbcTemplate.queryForObject("""
            SELECT quantity
            FROM hub.p_inventories
            WHERE id = ?
            """, Integer.class, inventoryId);

        assertThat(storedQuantity).isEqualTo(70);

        // 해당 주문의 작업이 정확히 한 건이며, 응답의 작업 ID와 일치해야 한다.
        List<Map<String, Object>> operations = jdbcTemplate.queryForList("""
            SELECT id, idempotency_key, hub_id, product_id,
                   inventory_id, operation_type, operation_status,
                   requested_quantity, remaining_quantity
            FROM hub.p_inventory_operations
            WHERE order_id = ?
            """, orderId);

        assertThat(operations).hasSize(1);
        assertThat(operations.get(0))
                .containsEntry("id", operationId)
                .containsEntry("idempotency_key", idempotencyKey)
                .containsEntry("hub_id", hubId)
                .containsEntry("product_id", productId)
                .containsEntry("inventory_id", inventoryId)
                .containsEntry("operation_type", "DECREASE")
                .containsEntry("operation_status", "SUCCEEDED")
                .containsEntry("requested_quantity", 30)
                .containsEntry("remaining_quantity", 70);

        // 실제 변경 이력도 한 건이며, 같은 작업과 주문에 연결되어야 한다.
        List<Map<String, Object>> histories = jdbcTemplate.queryForList("""
        SELECT operation_id, order_id, change_type,
               stock_change, stock_after
        FROM hub.p_inventory_histories
        WHERE inventory_id = ?
        """, inventoryId);

        assertThat(histories).hasSize(1);
        assertThat(histories.get(0))
                .containsEntry("operation_id", operationId)
                .containsEntry("order_id", orderId)
                .containsEntry("change_type", "DECREASE")
                .containsEntry("stock_change", -30)
                .containsEntry("stock_after", 70);
    }

    @Test
    @DisplayName("이력저장 실패하면 재고참과 작업기록 롤백")
    void rollbackDecreaseWhenHistorySaveFails() throws Exception{
        UUID orderId = UUID.randomUUID();

        // 재고이력에 insert 거절하도록 임시 제약조건 세팅
        jdbcTemplate.execute("""
                ALTER TABLE hub.p_inventory_histories
                            ADD CONSTRAINT ck_test_history_write_failure
                            CHECK (inventory_id <> '%s'::uuid)
                """.formatted(inventoryId));

        try {
            MvcResult result = mockMvc.perform(
                            post("/internal/v1/inventories/decrease")
                                    .header(
                                            "Idempotency-Key",
                                            "order:" + orderId + ":decrease"
                                    )
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                        {
                                          "orderId": "%s",
                                          "hubId": "%s",
                                          "productId": "%s",
                                          "quantity": 30
                                        }
                                        """.formatted(orderId, hubId, productId))
                    )
                    .andExpect(status().isInternalServerError())
                    .andReturn();

            // 다른 오류가 아니라 의도한 이력 저장 실패인지 확인한다.
            assertThat(result.getResolvedException())
                    .hasStackTraceContaining("ck_test_history_write_failure");

            Integer storedQuantity = jdbcTemplate.queryForObject("""
                SELECT quantity
                FROM hub.p_inventories
                WHERE id = ?
                """, Integer.class, inventoryId);

            Long operationCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM hub.p_inventory_operations
                WHERE order_id = ?
                """, Long.class, orderId);

            Long historyCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM hub.p_inventory_histories
                WHERE inventory_id = ?
                """, Long.class, inventoryId);

            assertThat(storedQuantity).isEqualTo(100);
            assertThat(operationCount).isZero();
            assertThat(historyCount).isZero();
        } finally {
            // 테스트가 실패하더라도 임시 제약조건은 제거한다.
            jdbcTemplate.execute("""
                ALTER TABLE hub.p_inventory_histories
                DROP CONSTRAINT ck_test_history_write_failure
                """);
        }

    }



}

