package com.hub.inventory;

import com.common.auth.AuthContract;
import com.common.auth.AuthRole;
import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.hub.inventory.domain.client.ProductClient;
import com.hub.inventory.domain.client.ProductInfo;
import com.hub.inventory.domain.entity.Inventory;
import com.hub.inventory.domain.repository.InventoryRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
@ActiveProfiles("test")
@Import(TestPostgresConnectConfig.class)
public class InventorySearchIntegrationTest {

    private static final UUID HUB_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID PRODUCT_ID =
            UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID INVENTORY_ID =
            UUID.fromString("50000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_HUB_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID OTHER_PRODUCT_ID =
            UUID.fromString("30000000-0000-4000-8000-000000000002");
    private static final UUID SAME_HUB_INVENTORY_ID =
            UUID.fromString("50000000-0000-4000-8000-000000000002");
    private static final UUID SAME_PRODUCT_INVENTORY_ID =
            UUID.fromString("50000000-0000-4000-8000-000000000003");


    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoBean
    private ProductClient productClient;
    @Autowired
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void setUp() {
        clearTestData();
    }

    @AfterEach
    void tearDown() {
        clearTestData();
    }

    /**
     *  테스트 코드
     */
    @Test
    @DisplayName("조건에 맞는 재고가 없으면 빈 배열을 반환한다")
    void returnsEmptyListWhenNoInventoryMatches() throws Exception {
        requestInventories(HUB_ID, PRODUCT_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("저장된 재고를 조회하면 재고 정보를 반환")
    void returnsStoredInventory() throws Exception {
        insertTestInventory();

        requestInventories(HUB_ID, PRODUCT_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(INVENTORY_ID.toString()))
                .andExpect(jsonPath("$[0].hubId").value(HUB_ID.toString()))
                .andExpect(jsonPath("$[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$[0].quantity").value(100));
    }

    @Test
    @DisplayName("허브 ID와 상품 ID가 모두 일치하는 재고만 반환한다")
    void searchesInventoryByHubAndProductIds() throws Exception {
        insertTestInventory();
        insertOtherInventoriesForSearch();

        requestInventories(HUB_ID, PRODUCT_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(INVENTORY_ID.toString()))
                .andExpect(jsonPath("$[0].hubId").value(HUB_ID.toString()))
                .andExpect(jsonPath("$[0].productId").value(PRODUCT_ID.toString()));
    }

    @Test
    @DisplayName("허브 ID만 지정하면 해당 허브의 재고만 반환한다")
    void searchesInventoriesByHubId() throws Exception {
        insertTestInventory();
        insertOtherInventoriesForSearch();

        requestInventories(HUB_ID, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        INVENTORY_ID.toString(),
                        SAME_HUB_INVENTORY_ID.toString()
                )));
    }

    @Test
    @DisplayName("상품 ID만 지정하면 해당 상품의 허브별 재고만 반환한다")
    void searchesInventoriesByProductId() throws Exception {
        insertTestInventory();
        insertOtherInventoriesForSearch();

        requestInventories(null, PRODUCT_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        INVENTORY_ID.toString(),
                        SAME_PRODUCT_INVENTORY_ID.toString()
                )));
    }

    @Test
    @DisplayName("검색 조건이 없으면 전체 재고를 반환한다")
    void returnsAllInventoriesWhenNoConditionsProvided() throws Exception {
        insertTestInventory();
        insertOtherInventoriesForSearch();

        requestInventories(null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        INVENTORY_ID.toString(),
                        SAME_HUB_INVENTORY_ID.toString(),
                        SAME_PRODUCT_INVENTORY_ID.toString()
                )));
    }

    @Test
    @DisplayName("전체 조회에서 논리 삭제된 재고를 제외한다")
    void excludesDeletedInventoriesFromList() throws Exception {
        insertTestInventory();
        insertOtherInventoriesForSearch();
        markInventoryAsDeleted(INVENTORY_ID);

        requestInventories(null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        SAME_HUB_INVENTORY_ID.toString(),
                        SAME_PRODUCT_INVENTORY_ID.toString()
                )));
    }

    @Test
    @DisplayName("허브·상품 조건에 일치해도 논리 삭제된 재고는 반환하지 않는다")
    void excludesDeletedInventoryWhenSearchingByHubAndProductIds()
            throws Exception {
        insertTestInventory();
        insertOtherInventoriesForSearch();
        markInventoryAsDeleted(INVENTORY_ID);

        requestInventories(HUB_ID, PRODUCT_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("재고 목록은 기본적으로 생성일 내림차순")
    void returnsInventoriesIncreatedAtDescDefault() throws Exception{
        insertTestInventory();
        insertOtherInventoriesForSearch();

        requestInventories(null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].id", contains(
                        SAME_PRODUCT_INVENTORY_ID.toString(),
                        SAME_HUB_INVENTORY_ID.toString(),
                        INVENTORY_ID.toString()
                )));
    }


    @Test
    @DisplayName("초기 수량이 음수면 재고 등록 실패")
    void rejectsRegistrationWhenInitialQuantityIsNegative() throws Exception {
        mockMvc.perform(post("/api/v1/inventories")
                        .header(
                                AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001"
                        )
                        .header(
                                AuthContract.HEADER_USER_ROLE,
                                AuthRole.MASTER.name()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "hubId": "%s",
                          "productId": "%s",
                          "initialQuantity": -1
                        }
                        """.formatted(HUB_ID, PRODUCT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        requestInventories(HUB_ID, PRODUCT_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("관리자는 재고를 등록하고 등록한 재고를 조회할 수 있다")
    void createsInventoryAndReturnsStoredInventory() throws Exception {
        insertTestInventory();

        given(productClient.findActiveProduct(OTHER_PRODUCT_ID))
                .willReturn(Optional.of(new ProductInfo(
                        OTHER_PRODUCT_ID,
                        UUID.fromString("60000000-0000-4000-8000-000000000001"),
                        HUB_ID,
                        UUID.fromString("20000000-0000-4000-8000-000000000002")
                )));

        mockMvc.perform(post("/api/v1/inventories")
                        .header(
                                AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001"
                        )
                        .header(
                                AuthContract.HEADER_USER_ROLE,
                                AuthRole.MASTER.name()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "hubId": "%s",
                              "productId": "%s",
                              "initialQuantity": 50
                            }
                            """.formatted(HUB_ID, OTHER_PRODUCT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.hubId").value(HUB_ID.toString()))
                .andExpect(jsonPath("$.productId").value(OTHER_PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.quantity").value(50));

        requestInventories(HUB_ID, OTHER_PRODUCT_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].hubId").value(HUB_ID.toString()))
                .andExpect(jsonPath("$[0].productId").value(OTHER_PRODUCT_ID.toString()))
                .andExpect(jsonPath("$[0].quantity").value(50));
    }

    @Test
    @DisplayName("DB에서 발견한 재고 중복을 업무 오류로 변환하고 기존 재고를 유지한다")
    void convertsDuplicateInventoryConstraintViolationToBusinessError()
            throws Exception {
        insertTestInventory();

        TransactionTemplate writeTransaction =
                new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
                writeTransaction.execute(status ->
                        inventoryRepository.save(
                                Inventory.create(HUB_ID, PRODUCT_ID, 50)
                        )
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT)
        );

        requestInventories(HUB_ID, PRODUCT_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(INVENTORY_ID.toString()))
                .andExpect(jsonPath("$[0].quantity").value(100));
    }


    @Test
    @DisplayName("해당 상품 소유 업체의 매니저는 재고를 등록할 수 있다")
    void allowsCompanyManagerToCreateInventory() throws Exception {
        insertTestInventory();

        UUID managerId =
                UUID.fromString("20000000-0000-4000-8000-000000000002");

        given(productClient.findActiveProduct(OTHER_PRODUCT_ID))
                .willReturn(Optional.of(new ProductInfo(
                        OTHER_PRODUCT_ID,
                        UUID.fromString("60000000-0000-4000-8000-000000000001"),
                        HUB_ID,
                        managerId
                )));

        mockMvc.perform(post("/api/v1/inventories")
                        .header(
                                AuthContract.HEADER_USER_ID,
                                managerId.toString()
                        )
                        .header(
                                AuthContract.HEADER_USER_ROLE,
                                AuthRole.USER.name()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "hubId": "%s",
                              "productId": "%s",
                              "initialQuantity": 50
                            }
                            """.formatted(HUB_ID, OTHER_PRODUCT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.hubId").value(HUB_ID.toString()))
                .andExpect(jsonPath("$.productId").value(OTHER_PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.quantity").value(50));

        requestInventories(HUB_ID, OTHER_PRODUCT_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].hubId").value(HUB_ID.toString()))
                .andExpect(jsonPath("$[0].productId").value(OTHER_PRODUCT_ID.toString()))
                .andExpect(jsonPath("$[0].quantity").value(50));
    }


    @Test
    @DisplayName("해당 업체 매니저가 아닌 USER는 재고를 등록할 수 없다")
    void rejectsRegistrationWhenUserIsNotCompanyManager() throws Exception {
        insertTestInventory();

        UUID managerId =
                UUID.fromString("20000000-0000-4000-8000-000000000002");
        UUID otherUserId =
                UUID.fromString("20000000-0000-4000-8000-000000000003");

        given(productClient.findActiveProduct(OTHER_PRODUCT_ID))
                .willReturn(Optional.of(new ProductInfo(
                        OTHER_PRODUCT_ID,
                        UUID.fromString("60000000-0000-4000-8000-000000000001"),
                        HUB_ID,
                        managerId
                )));

        mockMvc.perform(post("/api/v1/inventories")
                        .header(
                                AuthContract.HEADER_USER_ID,
                                otherUserId.toString()
                        )
                        .header(
                                AuthContract.HEADER_USER_ROLE,
                                AuthRole.USER.name()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "hubId": "%s",
                              "productId": "%s",
                              "initialQuantity": 50
                            }
                            """.formatted(HUB_ID, OTHER_PRODUCT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        requestInventories(HUB_ID, OTHER_PRODUCT_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("공급업체에 지정된 관리 허브가 아닌 다른 허브에는 재고 등록불가")
    void rejectsRegistrationWhenHubDoesNotMatchCompanyHub() throws Exception {
        // 공급업체의 관리 허브와, 잘못 등록을 요청할 다른 허브를 모두 준비한다.
        insertTestInventory();
        insertOtherInventoriesForSearch();

        // 등록할 상품의 공급업체는 HUB_ID를 관리 허브로 사용한다.
        given(productClient.findActiveProduct(OTHER_PRODUCT_ID))
                .willReturn(Optional.of(new ProductInfo(
                        OTHER_PRODUCT_ID,
                        UUID.fromString("60000000-0000-4000-8000-000000000001"),
                        HUB_ID,
                        UUID.fromString("20000000-0000-4000-8000-000000000002")
                )));

        // MASTER가 공급업체의 관리 허브(HUB_ID)가 아닌
        // 다른 허브(OTHER_HUB_ID)에 해당 상품의 재고 등록을 요청한다.
        mockMvc.perform(post("/api/v1/inventories")
                        .header(
                                AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001"
                        )
                        .header(
                                AuthContract.HEADER_USER_ROLE,
                                AuthRole.MASTER.name()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "hubId": "%s",
                              "productId": "%s",
                              "initialQuantity": 50
                            }
                            """.formatted(
                                OTHER_HUB_ID,
                                OTHER_PRODUCT_ID
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        // 등록을 거절했으므로, 다른 허브에 해당 상품의 재고가 저장되지 않아야 한다.
        requestInventories(OTHER_HUB_ID, OTHER_PRODUCT_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }


    @Test
    @DisplayName("등록할 상품 또는 소유 업체를 찾을 수 없으면 404 반환")
    void rejectsRegistrationWhenProductIsUnavailable() throws Exception {
        insertTestInventory();

        // 상품 조회 결과, 등록 가능한 상품 또는 소유 업체를 찾지 못한 상황이다.
        given(productClient.findActiveProduct(OTHER_PRODUCT_ID))
                .willReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/inventories")
                        .header(
                                AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001"
                        )
                        .header(
                                AuthContract.HEADER_USER_ROLE,
                                AuthRole.MASTER.name()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "hubId": "%s",
                              "productId": "%s",
                              "initialQuantity": 50
                            }
                            """.formatted(HUB_ID, OTHER_PRODUCT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 등록을 거절했으므로, 요청한 상품의 재고가 저장되지 않아야 한다.
        requestInventories(HUB_ID, OTHER_PRODUCT_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }



    /**
     *  공통 메소드
     */
    // 검색 조건과 관리자 계정 헤더를 포함하여 재고 목록  요청
    private ResultActions requestInventories(
            UUID hubId,
            UUID productId
    ) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/inventories");

        if(hubId != null){
            request.param("hubId", hubId.toString());
        }
        if(productId != null){
            request.param("productId", productId.toString());
        }

        return mockMvc.perform(request
                .header(
                        AuthContract.HEADER_USER_ID,
                        "20000000-0000-4000-8000-000000000001"
                )
                .header(
                        AuthContract.HEADER_USER_ROLE,
                        AuthRole.MASTER.name()
                )
        );
    }

    // 조회테스트에 사용할 hub 와 재고수량 100개를 저장한다.
    private void insertTestInventory() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                INSERT INTO hub.p_hubs (
                    id, name, address, created_at
                )
                VALUES (
                    :hubId,
                    '재고 조회 테스트 Hub',
                    '테스트 주소',
                    CURRENT_TIMESTAMP
                )
                """)
                    .setParameter("hubId", HUB_ID)
                    .executeUpdate();

            entityManager.createNativeQuery("""
                INSERT INTO hub.p_inventories (
                    id, hub_id, product_id, quantity, created_at
                )
                VALUES (
                    :inventoryId,
                    :hubId,
                    :productId,
                    100,
                    TIMESTAMP '2026-01-01 09:00:00'
                )
                """)
                    .setParameter("inventoryId", INVENTORY_ID)
                    .setParameter("hubId", HUB_ID)
                    .setParameter("productId", PRODUCT_ID)
                    .executeUpdate();
        });
    }

    // 기본 재고 준비 후, 허브만 같거나 상품만 같은 비교용 재고를 추가.
    private void insertOtherInventoriesForSearch() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                INSERT INTO hub.p_hubs (
                    id, name, address, created_at
                )
                VALUES (
                    :hubId,
                    '다른 재고 조회 테스트 Hub',
                    '다른 테스트 주소',
                    CURRENT_TIMESTAMP
                )
                """)
                    .setParameter("hubId", OTHER_HUB_ID)
                    .executeUpdate();

            entityManager.createNativeQuery("""
                INSERT INTO hub.p_inventories (
                    id, hub_id, product_id, quantity, created_at
                )
                VALUES
                    (
                        :sameHubInventoryId,
                        :hubId,
                        :otherProductId,
                        50,
                        TIMESTAMP '2026-01-02 09:00:00'
                    ),
                    (
                        :sameProductInventoryId,
                        :otherHubId,
                        :productId,
                        200,
                        TIMESTAMP '2026-01-03 09:00:00'
                    )
                """)
                    .setParameter("sameHubInventoryId", SAME_HUB_INVENTORY_ID)
                    .setParameter("sameProductInventoryId", SAME_PRODUCT_INVENTORY_ID)
                    .setParameter("hubId", HUB_ID)
                    .setParameter("otherHubId", OTHER_HUB_ID)
                    .setParameter("productId", PRODUCT_ID)
                    .setParameter("otherProductId", OTHER_PRODUCT_ID)
                    .executeUpdate();
        });
    }

    // 지정한 재고에 삭제 일시와 삭제자를 기록하여 논리 삭제 상태로 만든다.
    private void markInventoryAsDeleted(UUID inventoryId) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
            UPDATE hub.p_inventories
            SET deleted_at = CURRENT_TIMESTAMP,
                deleted_by = :deletedBy
            WHERE id = :inventoryId
            """)
                    .setParameter("inventoryId", inventoryId)
                    .setParameter(
                            "deletedBy",
                            UUID.fromString(
                                    "20000000-0000-4000-8000-000000000001"
                            )
                    )
                    .executeUpdate();
        });
    }


    //테스트용 데이터 삭제 (재고, 허브)
    private void clearTestData() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createQuery("delete from Inventory")
                    .executeUpdate();

            entityManager.createQuery("""
                delete from Hub h
                where h.id in (:hubId, :otherHubId)
                """)
                    .setParameter("hubId", HUB_ID)
                    .setParameter("otherHubId", OTHER_HUB_ID)
                    .executeUpdate();
        });
    }
}
