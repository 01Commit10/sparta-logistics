package com.hub.hubroute;

import com.common.auth.AuthContract;
import com.common.auth.AuthRole;
import com.hub.support.TestPostgresConnectConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
@ActiveProfiles("test")
@Import(TestPostgresConnectConfig.class)
public class HubRouteSearchIntegrationTest {

    private static final UUID ROUTE_ID =
            UUID.fromString("40000000-0000-4000-8000-000000000001");
    private static final UUID REVERSE_ROUTE_ID =
            UUID.fromString("40000000-0000-4000-8000-000000000002");
    private static final UUID OTHER_HUB_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000003");
    private static final UUID FROM_HUB_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID TO_HUB_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000002");


    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    // 각 테스트를 데이터가 없는 상태에서 시작.
    @BeforeEach
    void setUp() {
        clearTestData();
    }

    // 다른 테스트에 준비한 데이터가 남지 않도록 정리.
    @AfterEach
    void tearDown() {
        clearTestData();
    }

    /**
     *  테스트코드
     */
    @Test
    @DisplayName("Hub Route 없으면 빈배열 반환")
    void returnsEmptyListWhenNoRoutes() throws Exception {
        requestHubRoutes()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("존재하는 Hub Route 조회")
    void returnsStoredHubRoute() throws Exception {
        insertTestRoute();

        requestHubRoutes()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ROUTE_ID.toString()))
                .andExpect(jsonPath("$[0].fromHubId").value(FROM_HUB_ID.toString()))
                .andExpect(jsonPath("$[0].toHubId").value(TO_HUB_ID.toString()))
                .andExpect(jsonPath("$[0].distanceKm").value(43.04))
                .andExpect(jsonPath("$[0].durationMin").value(49));
    }

    @Test
    @DisplayName("존재하지 않는 Hub Route를 단일 조회하면 404를 반환")
    void returnsNotFoundWhenRouteDoesNotExist() throws Exception {
        requestHubRoute(ROUTE_ID)
                 .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("존재하는 Hub Route를 단일 조회하면 해당 경로 정보를 반환")
    void returnsHubRouteWhenIdExists() throws Exception {
        insertTestRoute();

        requestHubRoute(ROUTE_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ROUTE_ID.toString()))
                .andExpect(jsonPath("$.fromHubId").value(FROM_HUB_ID.toString()))
                .andExpect(jsonPath("$.toHubId").value(TO_HUB_ID.toString()))
                .andExpect(jsonPath("$.distanceKm").value(43.04))
                .andExpect(jsonPath("$.durationMin").value(49));
    }

    @Test
    @DisplayName("논리 삭제된 Hub Route를 단일 조회하면 404를 반환")
    void returnsNotFoundWhenRouteIsDeleted() throws Exception {
        insertTestRoute();
        markRouteAsDeleted(ROUTE_ID);

        requestHubRoute(ROUTE_ID)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("목록 조회에서 논리 삭제된 Hub Route를 제외")
    void excludesDeletedRoutesFromList() throws Exception {
        insertTestRoute();
        markRouteAsDeleted(ROUTE_ID);

        requestHubRoutes()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("정렬 조건을 전달하지 않으면 생성일 내림차순으로 반환")
    void returnsRoutesInCreatedAtDescendingOrderByDefault() throws Exception {
        insertTestRoute();
        insertReverseTestRoute();

        requestHubRoutes()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", contains(
                        REVERSE_ROUTE_ID.toString(),
                        ROUTE_ID.toString()
                )));
    }

    @Test
    @DisplayName("출발·도착 Hub ID 조합으로 경로 하나를 조회한다")
    void searchesRouteByFromAndToHubIds() throws Exception {
        insertTestRoute();
        insertOtherRoutesForPairSearch();

        requestHubRoute(FROM_HUB_ID, TO_HUB_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ROUTE_ID.toString()))
                .andExpect(jsonPath("$.fromHubId").value(FROM_HUB_ID.toString()))
                .andExpect(jsonPath("$.toHubId").value(TO_HUB_ID.toString()))
                .andExpect(jsonPath("$.distanceKm").value(43.04))
                .andExpect(jsonPath("$.durationMin").value(49));
    }

    @Test
    @DisplayName("출발·도착 Hub ID를 반대로 전달하면 역방향 경로를 반환한다")
    void returnsReverseRouteWhenHubPairIsReversed() throws Exception {
        insertTestRoute();
        insertReverseTestRoute();

        requestHubRoute(TO_HUB_ID, FROM_HUB_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(REVERSE_ROUTE_ID.toString()))
                .andExpect(jsonPath("$.fromHubId").value(TO_HUB_ID.toString()))
                .andExpect(jsonPath("$.toHubId").value(FROM_HUB_ID.toString()))
                .andExpect(jsonPath("$.distanceKm").value(44.08))
                .andExpect(jsonPath("$.durationMin").value(46));
    }

    @Test
    @DisplayName("출발·도착 Hub ID 조합에 해당하는 경로가 없으면 404를 반환한다")
    void returnsNotFoundWhenNoRouteMatchesHubPair() throws Exception {
        insertTestRoute();

        requestHubRoute(TO_HUB_ID, FROM_HUB_ID)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("출발·도착 Hub ID 조합의 경로가 논리 삭제되었으면 404를 반환한다")
    void returnsNotFoundWhenHubPairRouteIsDeleted() throws Exception {
        insertTestRoute();
        markRouteAsDeleted(ROUTE_ID);

        requestHubRoute(FROM_HUB_ID, TO_HUB_ID)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("출발 Hub ID를 전달하지 않으면 400을 반환한다")
    void returnsBadRequestWhenFromHubIdIsMissing() throws Exception {
        requestHubRoute(null, TO_HUB_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("fromHubId"));
    }

    @Test
    @DisplayName("도착 Hub ID를 전달하지 않으면 400을 반환한다")
    void returnsBadRequestWhenToHubIdIsMissing() throws Exception {
        requestHubRoute(FROM_HUB_ID, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("toHubId"));
    }

    /**
     * 공통메소드
     */
    // 테스트 DB에서 경로를 먼저 삭제하고, 참조 대상인 Hub를 삭제.
    private void clearTestData() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createQuery("delete from HubRoute")
                    .executeUpdate();

            entityManager.createQuery("delete from Hub")
                    .executeUpdate();
        });
    }

    // 검색 조건 없이 전체 경로 목록을 요청.
    private ResultActions requestHubRoutes() throws Exception {
        return performAsUser(get("/api/v1/hub-routes"));
    }

    // 출발·도착 Hub ID 조합으로 단일 조회를 요청하며, 누락 입력도 테스트할 수 있게 한다.
    private ResultActions requestHubRoute(
            UUID fromHubId,
            UUID toHubId
    ) throws Exception {
        MockHttpServletRequestBuilder request =
                get("/api/v1/hub-routes/fromtosearch");

        if (fromHubId != null) {
            request.param("fromHubId", fromHubId.toString());
        }

        if (toHubId != null) {
            request.param("toHubId", toHubId.toString());
        }

        return performAsUser(request);
    }


    // 로그인 사용자 정보를 헤더에 포함하여 지정한 ID의 경로를 조회한다.
    private ResultActions requestHubRoute(UUID routeId) throws Exception {
        return performAsUser(get("/api/v1/hub-routes/{routeId}", routeId));
    }

    // 모든 조회 요청에 동일한 테스트 사용자의 인증 헤더를 설정한다.
    private ResultActions performAsUser(
            MockHttpServletRequestBuilder request
    ) throws Exception {
        return mockMvc.perform(request
                .header(
                        AuthContract.HEADER_USER_ID,
                        "20000000-0000-4000-8000-000000000001"
                )
                .header(
                        AuthContract.HEADER_USER_ROLE,
                        AuthRole.USER.name()
                ));
    }

    // 조회 테스트에 사용할 두 Hub와 하나의 Hub Route를 저장.
    private void insertTestRoute() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                INSERT INTO hub.p_hubs (id, name, address, created_at)
                VALUES
                    (
                        :fromHubId,
                        '서울특별시 센터',
                        '서울 테스트 주소',
                        CURRENT_TIMESTAMP
                    ),
                    (
                        :toHubId,
                        '경기 북부 센터',
                        '북부 테스트 주소',
                        CURRENT_TIMESTAMP
                    )
                """)
                    .setParameter("fromHubId", FROM_HUB_ID)
                    .setParameter("toHubId", TO_HUB_ID)
                    .executeUpdate();

            entityManager.createNativeQuery("""
                INSERT INTO hub.p_hub_routes (
                    id,
                    from_hub_id,
                    to_hub_id,
                    distance_km,
                    duration_min,
                    created_at
                )
                VALUES (
                    :routeId,
                    :fromHubId,
                    :toHubId,
                    43.04,
                    49,
                    TIMESTAMP '2026-01-01 09:00:00'
                )
                """)
                    .setParameter("routeId", ROUTE_ID)
                    .setParameter("fromHubId", FROM_HUB_ID)
                    .setParameter("toHubId", TO_HUB_ID)
                    .executeUpdate();
        });
    }

    // 지정한 경로에 삭제 일시와 삭제자를 기록하여 논리 삭제 상태로 변경.
    private void markRouteAsDeleted(UUID routeId) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                UPDATE hub.p_hub_routes
                SET deleted_at = CURRENT_TIMESTAMP,
                    deleted_by = :deletedBy
                WHERE id = :routeId
                """)
                    .setParameter("routeId", routeId)
                    .setParameter(
                            "deletedBy",
                            UUID.fromString("20000000-0000-4000-8000-000000000001")
                    )
                    .executeUpdate();
        });
    }

    // 기존 두 Hub를 반대로 설정하여 역방향 경로를 생성하고 더 최근 생성일로 등록
    private void insertReverseTestRoute() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                INSERT INTO hub.p_hub_routes (
                    id,
                    from_hub_id,
                    to_hub_id,
                    distance_km,
                    duration_min,
                    created_at
                )
                VALUES (
                    :routeId,
                    :fromHubId,
                    :toHubId,
                    44.08,
                    46,
                    TIMESTAMP '2026-01-02 09:00:00'
                )
                """)
                    .setParameter("routeId", REVERSE_ROUTE_ID)
                    .setParameter("fromHubId", TO_HUB_ID)
                    .setParameter("toHubId", FROM_HUB_ID)
                    .executeUpdate();
        });
    }


    // 기본 경로 준비 후, 출발만 같거나 도착만 같은 비교용 경로를 추가한다.
    private void insertOtherRoutesForPairSearch() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                INSERT INTO hub.p_hubs (
                    id, name, address, created_at
                )
                VALUES (
                    :hubId,
                    '경기 남부 센터',
                    '남부 테스트 주소',
                    CURRENT_TIMESTAMP
                )
                """)
                    .setParameter("hubId", OTHER_HUB_ID)
                    .executeUpdate();

            entityManager.createNativeQuery("""
                INSERT INTO hub.p_hub_routes (
                    id,
                    from_hub_id,
                    to_hub_id,
                    distance_km,
                    duration_min,
                    created_at
                )
                VALUES
                    (
                        '40000000-0000-4000-8000-000000000003'::uuid,
                        :fromHubId,
                        :otherHubId,
                        50.00,
                        60,
                        TIMESTAMP '2026-01-01 09:00:00'
                    ),
                    (
                        '40000000-0000-4000-8000-000000000004'::uuid,
                        :otherHubId,
                        :toHubId,
                        70.00,
                        80,
                        TIMESTAMP '2026-01-01 09:00:00'
                    )
                """)
                    .setParameter("fromHubId", FROM_HUB_ID)
                    .setParameter("toHubId", TO_HUB_ID)
                    .setParameter("otherHubId", OTHER_HUB_ID)
                    .executeUpdate();
        });
    }



}
