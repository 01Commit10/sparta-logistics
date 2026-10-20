package com.hub.inventory;

import com.hub.inventory.infrastructure.persistence.InventoryOperationRepositoryImpl;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=false",
        "spring.jpa.show-sql=true",
        "spring.jpa.properties.hibernate.format_sql=true"
})
@AutoConfigureMockMvc
@Import(TestPostgresConnectConfig.class)
public class InventoryConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private InventoryOperationRepositoryImpl inventoryOperationRepository;

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
                "재고 동시성 테스트 허브 - " + hubId,
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
    @DisplayName("같은 키·같은 내용의 동시 요청은 한 번만 차감하고 같은 성공 결과를 반환")
    void returnsSameResultForConcurrentDuplicateRequests() throws Exception {
        UUID orderId = UUID.randomUUID();

        List<MvcResult> results = decreaseConcurrently(orderId, 30, 30);
        MvcResult first = results.get(0);
        MvcResult second = results.get(1);

        assertSingleDecrease(orderId, 70);

        assertThat(first.getResponse().getStatus())
                .as("첫 번째 요청의 HTTP 상태")
                .isEqualTo(200);
        assertThat(second.getResponse().getStatus())
                .as("두 번째 요청의 HTTP 상태")
                .isEqualTo(200);

        String firstOperationId = JsonPath.read(
                first.getResponse().getContentAsString(), "$.operationId"
        );
        String secondOperationId = JsonPath.read(
                second.getResponse().getContentAsString(), "$.operationId"
        );

        assertThat(firstOperationId)
                .isNotBlank()
                .isEqualTo(secondOperationId);
    }

    @Test
    @DisplayName("같은 키·다른 수량의 동시 요청은 하나만 성공하고 다른 요청은 충돌한다")
    void rejectsConcurrentRequestsWithSameKeyAndDifferentQuantities()
            throws Exception {
        UUID orderId = UUID.randomUUID();

        List<MvcResult> results = decreaseConcurrently(orderId, 30, 50);
        MvcResult first = results.get(0);
        MvcResult second = results.get(1);

        int firstStatus = first.getResponse().getStatus();
        int secondStatus = second.getResponse().getStatus();

        assertThat(List.of(firstStatus, secondStatus))
                .containsExactlyInAnyOrder(200, 409);

        MvcResult conflict = firstStatus == 409 ? first : second;
        String errorCode = JsonPath.read(
                conflict.getResponse().getContentAsString(), "$.code"
        );
        assertThat(errorCode).isEqualTo("IDEMPOTENCY_CONFLICT");

        int expectedQuantity = firstStatus == 200 ? 70 : 50;
        assertSingleDecrease(orderId, expectedQuantity);
    }

    @Test
    @DisplayName("같은 키의 동시 요청이 재고 부족이면 하나의 거절 결과를 재사용한다")
    void returnsSameRejectionForConcurrentDuplicateRequests()
            throws Exception {
        UUID orderId = UUID.randomUUID();

        List<MvcResult> results = decreaseConcurrently(orderId, 150, 150);

        // 두 요청 모두 멱등성 충돌이 아닌 재고 부족으로 응답해야 한다.
        for (MvcResult result : results) {
            assertThat(result.getResponse().getStatus()).isEqualTo(409);

            String errorCode = JsonPath.read(
                    result.getResponse().getContentAsString(), "$.code"
            );
            assertThat(errorCode).isEqualTo("INSUFFICIENT_STOCK");
        }

        Integer quantity = jdbcTemplate.queryForObject("""
        SELECT quantity
        FROM hub.p_inventories
        WHERE id = ?
        """, Integer.class, inventoryId);

        List<Map<String, Object>> operations = jdbcTemplate.queryForList("""
        SELECT operation_status, rejection_reason,
               requested_quantity, remaining_quantity
        FROM hub.p_inventory_operations
        WHERE order_id = ?
        """, orderId);

        Long historyCount = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM hub.p_inventory_histories
        WHERE order_id = ?
        """, Long.class, orderId);

        assertThat(quantity).isEqualTo(100);

        assertThat(operations).hasSize(1);
        assertThat(operations.get(0))
                .containsEntry("operation_status", "REJECTED")
                .containsEntry("rejection_reason", "INSUFFICIENT_STOCK")
                .containsEntry("requested_quantity", 150)
                .containsEntry("remaining_quantity", 100);

        assertThat(historyCount).isZero();
    }

    @Test
    @DisplayName("서로 다른 주문 1,000건이 동시에 1개씩 차감해도 초기 재고 100개만 판매된다")
    void decreasesOnlyAvailableStockForOneThousandDistinctRequests()
            throws Exception {
        int totalRequests = 1_000;
        int concurrentRequests = 20;
        List<Callable<MvcResult>> requests = new ArrayList<>();

        for (int i = 0; i < totalRequests; i++) {
            UUID orderId = UUID.randomUUID();
            String key = "order:" + orderId + ":decrease";
            requests.add(() -> requestDecrease(orderId, key, 1));
        }

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        List<UUID> successfulOperationIds = new ArrayList<>();
        int rejectedResponses = 0;

        try {
            // 요청은 총 1,000건이며, 최대 20개의 작업 스레드가 나누어 처리한다.
            List<Future<MvcResult>> responses = executor.invokeAll(
                    requests, 90, TimeUnit.SECONDS
            );

            for (Future<MvcResult> response : responses) {
                MvcResult result = response.get();
                int status = result.getResponse().getStatus();
                String body = result.getResponse().getContentAsString();

                if (status == 200) {
                    String operationId = JsonPath.read(body, "$.operationId");
                    successfulOperationIds.add(UUID.fromString(operationId));
                } else {
                    assertThat(status).as("재고 소진 후 HTTP 상태").isEqualTo(409);
                    String errorCode = JsonPath.read(body, "$.code");
                    assertThat(errorCode).isEqualTo("INSUFFICIENT_STOCK");
                    rejectedResponses++;
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(successfulOperationIds).hasSize(100).doesNotHaveDuplicates();
        assertThat(rejectedResponses).isEqualTo(900);

        Integer quantity = jdbcTemplate.queryForObject("""
            SELECT quantity
            FROM hub.p_inventories
            WHERE id = ?
            """, Integer.class, inventoryId);

        Map<String, Object> operations = jdbcTemplate.queryForMap("""
            SELECT COUNT(*) AS total_count,
                   COUNT(DISTINCT order_id) AS order_count,
                   COUNT(*) FILTER (
                       WHERE operation_status = 'SUCCEEDED'
                   ) AS succeeded_count,
                   COUNT(*) FILTER (
                       WHERE operation_status = 'REJECTED'
                         AND rejection_reason = 'INSUFFICIENT_STOCK'
                   ) AS rejected_count
            FROM hub.p_inventory_operations
            WHERE inventory_id = ?
            """, inventoryId);

        List<UUID> historyOperationIds = jdbcTemplate.queryForList("""
            SELECT operation_id
            FROM hub.p_inventory_histories
            WHERE inventory_id = ?
            """, UUID.class, inventoryId);

        List<Integer> stockChanges = jdbcTemplate.queryForList("""
            SELECT stock_change
            FROM hub.p_inventory_histories
            WHERE inventory_id = ?
            """, Integer.class, inventoryId);

        List<Integer> stockAfter = jdbcTemplate.queryForList("""
            SELECT stock_after
            FROM hub.p_inventory_histories
            WHERE inventory_id = ?
            """, Integer.class, inventoryId);

        assertThat(quantity).isZero();
        assertThat(operations)
                .containsEntry("total_count", 1_000L)
                .containsEntry("order_count", 1_000L)
                .containsEntry("succeeded_count", 100L)
                .containsEntry("rejected_count", 900L);
        assertThat(historyOperationIds)
                .containsExactlyInAnyOrderElementsOf(successfulOperationIds);
        assertThat(stockChanges).hasSize(100).containsOnly(-1);
        // 100번의 실제 차감 직후 수량은 99부터 0까지 한 번씩 기록되어야 한다.
        assertThat(stockAfter).hasSize(100).doesNotHaveDuplicates()
                .allMatch(remaining -> remaining >= 0 && remaining < 100);

        System.out.printf(
                "INVENTORY_CONCURRENCY_RESULT requests=%d workers=%d "
                        + "succeeded=%d rejected=%d quantity=%d operations=%s histories=%d%n",
                totalRequests, concurrentRequests, successfulOperationIds.size(),
                rejectedResponses, quantity, operations.get("total_count"),
                historyOperationIds.size()
        );
    }

    @ParameterizedTest(name = "초기 재고 {0}")
    @ValueSource(ints = {100, Integer.MAX_VALUE})
    @DisplayName("같은 키·같은 원본의 동시 복원 요청은 한 번만 복원하고 같은 결과를 반환한다")
    void restoresOnceForConcurrentDuplicateRestoreRequests(int initialQuantity)
            throws Exception {
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update("UPDATE hub.p_inventories SET quantity = ? WHERE id = ?",
                initialQuantity, inventoryId);

        // 복원할 원본 차감을 먼저 만든다.
        MvcResult decrease = requestDecrease(
                orderId, "order:" + orderId + ":decrease", 30
        );
        assertThat(decrease.getResponse().getStatus())
                .as("원본 차감의 HTTP 상태")
                .isEqualTo(200);

        String originalOperationId = JsonPath.read(
                decrease.getResponse().getContentAsString(), "$.operationId"
        );

        List<MvcResult> results = restoreConcurrently(
                "order:" + orderId + ":restore", originalOperationId
        );

        for (MvcResult result : results) {
            assertThat(result.getResponse().getStatus())
                    .as("복원 요청의 HTTP 상태")
                    .isEqualTo(200);
        }

        String firstOperationId = JsonPath.read(
                results.get(0).getResponse().getContentAsString(), "$.operationId"
        );
        String secondOperationId = JsonPath.read(
                results.get(1).getResponse().getContentAsString(), "$.operationId"
        );

        assertThat(firstOperationId)
                .isNotBlank()
                .isEqualTo(secondOperationId);

        Integer quantity = jdbcTemplate.queryForObject("""
            SELECT quantity FROM hub.p_inventories WHERE id = ?
            """, Integer.class, inventoryId);
        Long restoreOperations = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM hub.p_inventory_operations
            WHERE original_operation_id = ?
            """, Long.class, UUID.fromString(originalOperationId));
        Long restoreHistories = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM hub.p_inventory_histories
            WHERE order_id = ? AND change_type = 'RESTORE'
            """, Long.class, orderId);

        // 정상 수량과 int 최대값 모두 최초 수량으로 한 번만 복원되어야 한다.
        assertThat(quantity).isEqualTo(initialQuantity);
        assertThat(restoreOperations).isEqualTo(1L);
        assertThat(restoreHistories).isEqualTo(1L);
    }


    //공통 메소드
    // 같은 키의 두 요청이 최초 조회를 모두 마친 뒤 차감 시도
    private List<MvcResult> decreaseConcurrently(
            UUID orderId, int firstQuantity, int secondQuantity
    ) throws Exception {
        String key = "order:" + orderId + ":decrease";
        CountDownLatch lookupsCompleted = new CountDownLatch(2);

        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            lookupsCompleted.countDown();

            if (!lookupsCompleted.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("두 요청의 작업 조회가 완료되지 않았습니다.");
            }
            return result;
        }).when(inventoryOperationRepository).findByIdempotencyKey(key);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(() ->
                    requestDecrease(orderId, key, firstQuantity));
            Future<MvcResult> second = executor.submit(() ->
                    requestDecrease(orderId, key, secondQuantity));

            return List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    // 재고 차감 API를 호출한다.
    private MvcResult requestDecrease(
            UUID orderId, String key, int quantity
    ) throws Exception {
        String body = """
            {
              "orderId": "%s",
              "hubId": "%s",
              "productId": "%s",
              "quantity": %d
            }
            """.formatted(orderId, hubId, productId, quantity);

        return mockMvc.perform(
                post("/internal/v1/inventories/decrease")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        ).andReturn();
    }

    // 같은 키의 두 복원 요청이 최초 조회를 모두 마친 뒤 복원을 시도
    private List<MvcResult> restoreConcurrently(
            String key, String originalOperationId
    ) throws Exception {
        CountDownLatch lookupsCompleted = new CountDownLatch(2);

        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            lookupsCompleted.countDown();

            if (!lookupsCompleted.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("두 요청의 작업 조회가 완료되지 않았습니다.");
            }
            return result;
        }).when(inventoryOperationRepository).findByIdempotencyKey(key);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(() ->
                    requestRestore(key, originalOperationId));
            Future<MvcResult> second = executor.submit(() ->
                    requestRestore(key, originalOperationId));

            return List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    // 재고 복원 API를 호출한다.
    private MvcResult requestRestore(
            String key, String originalOperationId
    ) throws Exception {
        String body = """
            {
              "originalOperationId": "%s"
            }
            """.formatted(originalOperationId);

        return mockMvc.perform(
                post("/internal/v1/inventories/restore")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        ).andReturn();
    }


    // 재고가 기대 수량이고, 해당 주문의 작업과 변경 이력이 각각 한 건인지 검증한다.
    private void assertSingleDecrease(UUID orderId, int expectedQuantity) {
        Integer quantity = jdbcTemplate.queryForObject("""
            SELECT quantity FROM hub.p_inventories WHERE id = ?
            """, Integer.class, inventoryId);
        Long operations = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM hub.p_inventory_operations WHERE order_id = ?
            """, Long.class, orderId);
        Long histories = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM hub.p_inventory_histories WHERE order_id = ?
            """, Long.class, orderId);

        assertThat(quantity).isEqualTo(expectedQuantity);
        assertThat(operations).isEqualTo(1L);
        assertThat(histories).isEqualTo(1L);
    }

}
