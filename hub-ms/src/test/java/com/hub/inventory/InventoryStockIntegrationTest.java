package com.hub.inventory;

import com.hub.support.TestPostgresConnectConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Test
    @DisplayName("재고가 부족하면 거절을 작업에 저장하고 재고수량을 유지")
    void rejectsDecreaseWhenStockIsInsufficient() throws Exception {
        UUID orderId = UUID.randomUUID();
        String idempotencyKey = "order:" + orderId + ":decrease";

        // 100개 재고인데 101개 차감요청
        mockMvc.perform(
                                post("/internal/v1/inventories/decrease")
                                        .header("Idempotency-Key", idempotencyKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                          "orderId": "%s",
                                                          "hubId": "%s",
                                                          "productId": "%s",
                                                          "quantity": 101
                                                        }
                                                        """.formatted(orderId, hubId, productId))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        Integer storedQuantity = jdbcTemplate.queryForObject("""
            SELECT quantity
            FROM hub.p_inventories
            WHERE id = ?
            """, Integer.class, inventoryId);

        //차감 요청 거절되서 그대로 100개인지 확인
        assertThat(storedQuantity).isEqualTo(100);

        //해당 주문번호로 생성된 재고 결과 가져오
        List<Map<String, Object>> operations = jdbcTemplate.queryForList("""
        SELECT *
        FROM hub.p_inventory_operations
        WHERE order_id = ?
        """, orderId);

        //재고결과 예상한게 맞는지 체크
        assertThat(operations).hasSize(1);
        assertThat(operations.get(0))
                .containsEntry("inventory_id", inventoryId)
                .containsEntry("operation_type", "DECREASE")
                .containsEntry("operation_status", "REJECTED")
                .containsEntry("rejection_reason", "INSUFFICIENT_STOCK")
                .containsEntry("requested_quantity", 101)
                .containsEntry("remaining_quantity", 100);

        //실제로 재고 반영기록 없는지 재고 기록에서 체크
        Long historyCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM hub.p_inventory_histories
            WHERE order_id = ?
            """, Long.class, orderId);

        assertThat(historyCount).isZero();

    }

    @Test
    @DisplayName("재고 대상 자체가 없으면 404 반환후 거절 사유저장")
    void rejectsDecreaseWhenInventoryDoesNotExist() throws Exception{
        UUID orderId = UUID.randomUUID();
        UUID missingProductId = UUID.randomUUID();
        String idempotencyKey = "order:" + orderId + ":decrease";

        mockMvc.perform(
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
                                """.formatted(
                                        orderId,
                                        hubId,
                                        missingProductId
                                ))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVENTORY_NOT_FOUND"));

        List<Map<String, Object>> operations = jdbcTemplate.queryForList("""
            SELECT hub_id, product_id, inventory_id,
                   operation_status, rejection_reason,
                   requested_quantity, remaining_quantity
            FROM hub.p_inventory_operations
            WHERE order_id = ?
            """, orderId);

        assertThat(operations).hasSize(1);
        assertThat(operations.get(0))
                .containsEntry("hub_id", hubId)
                .containsEntry("product_id", missingProductId)
                .containsEntry("inventory_id", null)
                .containsEntry("operation_status", "REJECTED")
                .containsEntry("rejection_reason", "INVENTORY_NOT_FOUND")
                .containsEntry("requested_quantity", 30)
                .containsEntry("remaining_quantity", null);

        Long historyCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM hub.p_inventory_histories
            WHERE order_id = ?
            """, Long.class, orderId);

        assertThat(historyCount).isZero();
    }

    @Test
    @DisplayName("논리삭제된 재고는 차감요청을 대상없음으로 거절")
    void rejectsDecreaseWhenInventoryIsDeleted() throws Exception {
        UUID orderId = UUID.randomUUID();
        String idempotencyKey = "order:" + orderId +":decrease";

        jdbcTemplate.update("""
                        UPDATE hub.p_inventories
                        SET deleted_at = CURRENT_TIMESTAMP,
                            deleted_by = ?
                        WHERE id = ?
                        """, UUID.randomUUID(), inventoryId);

        mockMvc.perform(
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
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVENTORY_NOT_FOUND"));

        /** operation 예상 구조
         * [
         *   {
         *     "operation_status": "REJECTED",
         *     "rejection_reason": "INVENTORY_NOT_FOUND",
         *     "inventory_id": null,
         *     "remaining_quantity": null
         *   }
         * ]
         */
        List<Map<String, Object>> operations = jdbcTemplate.queryForList("""
            SELECT operation_status, rejection_reason,
                   inventory_id, remaining_quantity
            FROM hub.p_inventory_operations
            WHERE order_id = ?
            """, orderId);

            //배열 첫행 0번 꺼내서 값 확인, 배열기준에서는 사이즈 1개
        assertThat(operations).hasSize(1);
        assertThat(operations.get(0))
                .containsEntry("operation_status", "REJECTED")
                .containsEntry("rejection_reason", "INVENTORY_NOT_FOUND")
                .containsEntry("inventory_id", null)
                .containsEntry("remaining_quantity", null);

        //재고변경있는지 확인
        Integer storedQuantity = jdbcTemplate.queryForObject("""
            SELECT quantity
            FROM hub.p_inventories
            WHERE id = ?
            """, Integer.class, inventoryId);

        assertThat(storedQuantity).isEqualTo(100);

        //히스토리 안적혓는지 확인
        Long historyCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM hub.p_inventory_histories
            WHERE order_id = ?
            """, Long.class, orderId);

        assertThat(historyCount).isZero();
    }

    @Test
    @DisplayName("같은멱등키로 차감요청을 재전송하면 기존 성공결과를 반환")
    void returnsExistingResultForRepeatedDecrease() throws Exception {
        UUID orderId = UUID.randomUUID();
        String idempotencyKey = "order:" + orderId + ":decrease";

        String requestBody = """
            {
              "orderId": "%s",
              "hubId": "%s",
              "productId": "%s",
              "quantity": 30
            }
            """.formatted(orderId, hubId, productId);

        String firstResponseBody=mockMvc.perform(
                        post("/internal/v1/inventories/decrease")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String operationId = JsonPath.read(
                firstResponseBody,
                "$.operationId"
        );

        mockMvc.perform(
                        post("/internal/v1/inventories/decrease")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(operationId))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.changedQuantity").value(-30))
                .andExpect(jsonPath("$.remainingQuantity").value(70));

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
            WHERE order_id = ?
            """, Long.class, orderId);

        assertThat(storedQuantity).isEqualTo(70);
        assertThat(operationCount).isEqualTo(1L);
        assertThat(historyCount).isEqualTo(1L);
    }


    @Test
    @DisplayName("같은 멱등성 키로 다른 수량을 요청하면 충돌로 거절한다")
    void rejectsDecreaseWhenSameKeyHasDifferentQuantity() throws Exception {
        UUID orderId = UUID.randomUUID();
        String idempotencyKey = "order:" + orderId + ":decrease";

        String requestTemplate = """
        {
          "orderId": "%s",
          "hubId": "%s",
          "productId": "%s",
          "quantity": %d
        }
        """;

        // 첫 요청은 30개를 차감한다.
        mockMvc.perform(
                        post("/internal/v1/inventories/decrease")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestTemplate.formatted(
                                        orderId, hubId, productId, 30
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingQuantity").value(70));

        // 같은 키를 사용하면서 수량만 50개로 바꾸면 거절해야 한다.
        mockMvc.perform(
                        post("/internal/v1/inventories/decrease")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestTemplate.formatted(
                                        orderId, hubId, productId, 50
                                ))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        // 첫 요청의 차감만 유지되어야 한다.
        Integer storedQuantity = jdbcTemplate.queryForObject("""
        SELECT quantity
        FROM hub.p_inventories
        WHERE id = ?
        """, Integer.class, inventoryId);

        assertThat(storedQuantity).isEqualTo(70);
    }

    @Test
    @DisplayName("재고가 늘어난 뒤에도 같은 키·같은 내용의 재요청은 최초 거절 결과를 반환한다")
    void returnsExistingRejectionAfterStockIncreases() throws Exception {
        UUID orderId = UUID.randomUUID();
        String idempotencyKey = "order:" + orderId + ":decrease";

        String requestBody = """
        {
          "orderId": "%s",
          "hubId": "%s",
          "productId": "%s",
          "quantity": 150
        }
        """.formatted(orderId, hubId, productId);

        // 재고 100개에서 150개를 요청하므로 최초 요청은 거절된다.
        mockMvc.perform(
                        post("/internal/v1/inventories/decrease")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        // 입고 이후의 상황을 재현하기 위한 테스트 데이터 변경이다.
        jdbcTemplate.update("""
        UPDATE hub.p_inventories
        SET quantity = 200
        WHERE id = ?
        """, inventoryId);

        // 지금은 수량이 충분해도 같은 요청의 최초 거절 결과를 반환해야 한다.
        mockMvc.perform(
                        post("/internal/v1/inventories/decrease")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        List<Map<String, Object>> operations = jdbcTemplate.queryForList("""
        SELECT operation_status, rejection_reason, remaining_quantity
        FROM hub.p_inventory_operations
        WHERE idempotency_key = ?
        """, idempotencyKey);

        assertThat(operations).hasSize(1);
        assertThat(operations.get(0))
                .containsEntry("operation_status", "REJECTED")
                .containsEntry("rejection_reason", "INSUFFICIENT_STOCK")
                .containsEntry("remaining_quantity", 100);

        Integer storedQuantity = jdbcTemplate.queryForObject("""
        SELECT quantity
        FROM hub.p_inventories
        WHERE id = ?
        """, Integer.class, inventoryId);

        Long historyCount = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM hub.p_inventory_histories
        WHERE order_id = ?
        """, Long.class, orderId);

        assertThat(storedQuantity).isEqualTo(200);
        assertThat(historyCount).isZero();
    }

    @Test
    @DisplayName("성공한 원본 차감 기록을 기준으로 재고를 복원")
    void restoresStockFromSucceededDecrease() throws Exception {
        UUID orderId = UUID.randomUUID();

        // 먼저 재고를 100개에서 70개로 차감한다.
        String decreaseResponse = mockMvc.perform(
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
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String originalOperationId = JsonPath.read(
                decreaseResponse, "$.operationId"
        );

        // 차감 수량 대신 원본 작업 ID로 복원을 요청한다.
        mockMvc.perform(
                        post("/internal/v1/inventories/restore")
                                .header(
                                        "Idempotency-Key",
                                        "order:" + orderId + ":restore"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "originalOperationId": "%s"
                                }
                                """.formatted(originalOperationId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").isNotEmpty())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.inventoryId").value(inventoryId.toString()))
                .andExpect(jsonPath("$.changedQuantity").value(30))
                .andExpect(jsonPath("$.remainingQuantity").value(100));

        Integer quantity = jdbcTemplate.queryForObject("""
        SELECT quantity
        FROM hub.p_inventories
        WHERE id = ?
        """, Integer.class, inventoryId);

        assertThat(quantity).isEqualTo(100);
    }

    @ParameterizedTest(name = "초기 재고 {0}")
    @ValueSource(ints = {100, Integer.MAX_VALUE})
    @DisplayName("다른 키로 이미 복원된 원본 차감을 다시 복원하면 409로 거절하고 재고는 한 번만 복원한다")
    void rejectsRestoreWhenOriginalIsAlreadyRestored(int initialQuantity)
            throws Exception {
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update("UPDATE hub.p_inventories SET quantity = ? WHERE id = ?",
                initialQuantity, inventoryId);

        // 원본 차감 작업을 만든다.
        String decreaseResponse = mockMvc.perform(
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
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String originalOperationId = JsonPath.read(
                decreaseResponse, "$.operationId"
        );

        String restoreBody = """
                {
                  "originalOperationId": "%s"
                }
                """.formatted(originalOperationId);

        // 첫 복원으로 최초 수량으로 돌아온다.
        mockMvc.perform(
                        post("/internal/v1/inventories/restore")
                                .header(
                                        "Idempotency-Key",
                                        "order:" + orderId + ":restore"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(restoreBody)
                )
                .andExpect(status().isOk());

        // 같은 원본을 다른 키로 다시 복원하면 이미 복원된 원본으로 거절한다.
        mockMvc.perform(
                        post("/internal/v1/inventories/restore")
                                .header(
                                        "Idempotency-Key",
                                        "order:" + orderId + ":restore-again"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(restoreBody)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_ALREADY_RESTORED"));

        Integer quantity = jdbcTemplate.queryForObject("""
        SELECT quantity
        FROM hub.p_inventories
        WHERE id = ?
        """, Integer.class, inventoryId);

        Long restoreOperationCount = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM hub.p_inventory_operations
        WHERE original_operation_id = ?
        """, Long.class, UUID.fromString(originalOperationId));

        Long restoreHistoryCount = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM hub.p_inventory_histories
        WHERE order_id = ?
          AND change_type = 'RESTORE'
        """, Long.class, orderId);

        assertThat(quantity).isEqualTo(initialQuantity);
        assertThat(restoreOperationCount).isEqualTo(1L);
        assertThat(restoreHistoryCount).isEqualTo(1L);
    }

}

