package com.hub;

import com.common.auth.AuthContract;
import com.common.auth.AuthRole;
import com.hub.support.TestPostgresConnectConfig;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestPostgresConnectConfig.class)
class HubInternalApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID departureHubId;
    private UUID arrivalHubId;
    private UUID routeId;
    private UUID productId;
    private UUID otherProductId;
    private UUID departureInventoryId;
    private UUID arrivalInventoryId;

    @BeforeEach
    void setUp() {
        departureHubId = UUID.randomUUID();
        arrivalHubId = UUID.randomUUID();
        routeId = UUID.randomUUID();
        productId = UUID.randomUUID();
        otherProductId = UUID.randomUUID();
        departureInventoryId = UUID.randomUUID();
        arrivalInventoryId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO hub.p_hubs (id, name, address, created_at)
                VALUES (?, ?, '출발 주소', CURRENT_TIMESTAMP),
                       (?, ?, '도착 주소', CURRENT_TIMESTAMP)
                """, departureHubId, "출발 허브-" + departureHubId,
                arrivalHubId, "도착 허브-" + arrivalHubId);
        jdbcTemplate.update("""
                INSERT INTO hub.p_hub_routes
                    (id, from_hub_id, to_hub_id, distance_km, duration_min, created_at)
                VALUES (?, ?, ?, 43.04, 49, CURRENT_TIMESTAMP)
                """, routeId, departureHubId, arrivalHubId);
        jdbcTemplate.update("""
                INSERT INTO hub.p_inventories (id, hub_id, product_id, quantity, created_at)
                VALUES (?, ?, ?, 100, CURRENT_TIMESTAMP),
                       (?, ?, ?, 200, CURRENT_TIMESTAMP),
                       (?, ?, ?, 300, CURRENT_TIMESTAMP)
                """, departureInventoryId, departureHubId, productId,
                arrivalInventoryId, arrivalHubId, productId,
                UUID.randomUUID(), departureHubId, otherProductId);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("""
                DELETE FROM hub.p_inventory_histories
                WHERE inventory_id IN (?, ?)
                """, departureInventoryId, arrivalInventoryId);
        jdbcTemplate.update("DELETE FROM hub.p_inventory_operations WHERE product_id = ?", productId);
        jdbcTemplate.update("DELETE FROM hub.p_inventories WHERE product_id IN (?, ?)", productId, otherProductId);
        jdbcTemplate.update("DELETE FROM hub.p_hub_routes WHERE id = ?", routeId);
        jdbcTemplate.update("DELETE FROM hub.p_hubs WHERE id IN (?, ?)", departureHubId, arrivalHubId);
    }

    @Test
    @DisplayName("내부 허브 조회는 사용자 헤더 없이 활성 허브 정보를 반환한다")
    void returnsActiveHub() throws Exception {
        mockMvc.perform(get("/internal/v1/hubs/{hubId}", departureHubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(departureHubId.toString()))
                .andExpect(jsonPath("$.name").value("출발 허브-" + departureHubId))
                .andExpect(jsonPath("$.address").value("출발 주소"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("없거나 삭제된 허브는 내부 조회에서 404를 반환한다")
    void rejectsMissingOrDeletedHub(boolean deleted) throws Exception {
        UUID requestedId = UUID.randomUUID();
        if (deleted) {
            jdbcTemplate.update("UPDATE hub.p_hubs SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", departureHubId);
            requestedId = departureHubId;
        }

        mockMvc.perform(get("/internal/v1/hubs/{hubId}", requestedId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("내부 경로 조회는 departureHubId와 arrivalHubId로 매핑한 객체를 반환한다")
    void returnsRouteUsingInternalFieldNames() throws Exception {
        mockMvc.perform(get("/internal/v1/hub-routes")
                        .param("departureHubId", departureHubId.toString())
                        .param("arrivalHubId", arrivalHubId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(routeId.toString()))
                .andExpect(jsonPath("$.departureHubId").value(departureHubId.toString()))
                .andExpect(jsonPath("$.arrivalHubId").value(arrivalHubId.toString()))
                .andExpect(jsonPath("$.distanceKm").value(43.04))
                .andExpect(jsonPath("$.durationMin").value(49));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("없거나 삭제된 경로는 내부 조회에서 404를 반환한다")
    void rejectsMissingOrDeletedRoute(boolean deleted) throws Exception {
        UUID requestedArrival = UUID.randomUUID();
        if (deleted) {
            jdbcTemplate.update("UPDATE hub.p_hub_routes SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", routeId);
            requestedArrival = arrivalHubId;
        }

        mockMvc.perform(get("/internal/v1/hub-routes")
                        .param("departureHubId", departureHubId.toString())
                        .param("arrivalHubId", requestedArrival.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"departureHubId", "arrivalHubId"})
    @DisplayName("내부 경로 조회는 출발·도착 파라미터가 모두 필요하다")
    void requiresBothRouteParameters(String missingParameter) throws Exception {
        MockHttpServletRequestBuilder request = get("/internal/v1/hub-routes");
        if (missingParameter.equals("departureHubId")) {
            request.param("arrivalHubId", arrivalHubId.toString());
        } else {
            request.param("departureHubId", departureHubId.toString());
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value(missingParameter));
    }

    @Test
    @DisplayName("상품의 모든 허브 재고를 논리 삭제하고 다른 상품과 수량은 보존한다")
    void softDeletesAllInventoriesForProduct() throws Exception {
        UUID actorId = UUID.randomUUID();

        deleteInventories(productId, actorId).andExpect(status().isNoContent());

        List<Map<String, Object>> rows = inventoryRows(productId);
        assertThat(rows).hasSize(2).allSatisfy(row -> {
            assertThat(row.get("deleted_at")).isNotNull();
            assertThat(row.get("deleted_by")).isEqualTo(actorId);
            assertThat(row.get("updated_at")).isNotNull();
            assertThat(row.get("updated_by")).isEqualTo(actorId);
        });
        assertThat(rows).extracting(row -> row.get("quantity"))
                .containsExactlyInAnyOrder(100, 200);
        assertThat(inventoryRows(otherProductId)).singleElement().satisfies(row -> {
            assertThat(row.get("deleted_at")).isNull();
            assertThat(row.get("quantity")).isEqualTo(300);
        });

        mockMvc.perform(get("/api/v1/inventories")
                        .param("productId", productId.toString())
                        .header(AuthContract.HEADER_USER_ID, actorId)
                        .header(AuthContract.HEADER_USER_ROLE, AuthRole.USER.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("반복 삭제는 최초 삭제 시각과 작업자를 보존한다")
    void preservesFirstDeletionOnRetry() throws Exception {
        deleteInventories(productId, UUID.randomUUID()).andExpect(status().isNoContent());
        List<Map<String, Object>> firstDeletion = inventoryRows(productId);

        deleteInventories(productId, UUID.randomUUID()).andExpect(status().isNoContent());

        assertThat(inventoryRows(productId)).isEqualTo(firstDeletion);
    }

    @Test
    @DisplayName("이전에 삭제된 재고는 상품 전체 삭제 요청에서도 그대로 유지한다")
    void preservesAlreadyDeletedInventory() throws Exception {
        jdbcTemplate.update("""
                UPDATE hub.p_inventories SET deleted_at = ?, deleted_by = ? WHERE id = ?
                """, LocalDateTime.of(2026, 1, 1, 9, 0), UUID.randomUUID(), departureInventoryId);
        Map<String, Object> before = jdbcTemplate.queryForMap("""
                SELECT * FROM hub.p_inventories WHERE id = ?
                """, departureInventoryId);

        deleteInventories(productId, UUID.randomUUID()).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForMap("SELECT * FROM hub.p_inventories WHERE id = ?", departureInventoryId))
                .isEqualTo(before);
    }

    @Test
    @DisplayName("재고가 없는 상품도 정리 요청에 204를 반환한다")
    void acceptsProductWithoutInventory() throws Exception {
        deleteInventories(UUID.randomUUID(), UUID.randomUUID()).andExpect(status().isNoContent());
        assertThat(inventoryRows(productId)).allSatisfy(row -> assertThat(row.get("deleted_at")).isNull());
    }

    @Test
    @DisplayName("사용자 없는 서비스 호출은 시스템 UUID를 삭제자로 기록한다")
    void recordsSystemActorWithoutUserHeaders() throws Exception {
        deleteInventories(productId, null).andExpect(status().isNoContent());

        assertThat(inventoryRows(productId)).hasSize(2)
                .allSatisfy(row -> assertThat(row.get("deleted_by")).isEqualTo(AuthContract.SYSTEM_USER_ID));
    }

    @Test
    @DisplayName("재고를 논리 삭제해도 차감 Operation과 History는 보존한다")
    void preservesStockOperationAndHistory() throws Exception {
        UUID orderId = UUID.randomUUID();
        mockMvc.perform(post("/internal/v1/inventories/decrease")
                        .header("Idempotency-Key", "order:" + orderId + ":decrease")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","hubId":"%s","productId":"%s","quantity":30}
                                """.formatted(orderId, departureHubId, productId)))
                .andExpect(status().isOk());

        deleteInventories(productId, UUID.randomUUID()).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("""
                SELECT quantity FROM hub.p_inventories WHERE id = ?
                """, Integer.class, departureInventoryId)).isEqualTo(70);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM hub.p_inventory_operations WHERE order_id = ?
                """, Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM hub.p_inventory_histories WHERE order_id = ?
                """, Integer.class, orderId)).isEqualTo(1);
    }

    private ResultActions deleteInventories(UUID targetProductId, UUID actorId) throws Exception {
        MockHttpServletRequestBuilder request =
                delete("/internal/v1/inventories/products/{productId}", targetProductId);
        if (actorId != null) {
            request.header(AuthContract.HEADER_USER_ID, actorId)
                    .header(AuthContract.HEADER_USER_ROLE, AuthRole.USER.name());
        }
        return mockMvc.perform(request);
    }

    private List<Map<String, Object>> inventoryRows(UUID targetProductId) {
        return jdbcTemplate.queryForList("""
                SELECT id, quantity, deleted_at, deleted_by, updated_at, updated_by
                FROM hub.p_inventories WHERE product_id = ? ORDER BY id
                """, targetProductId);
    }
}
