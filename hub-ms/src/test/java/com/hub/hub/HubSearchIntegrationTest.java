package com.hub.hub;

import com.common.auth.AuthContract;
import com.common.auth.AuthRole;
import com.hub.support.TestPostgresConnectConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
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
public class HubSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;


    // 각 테스트 전에 기존 데이터를 비우고 검색·정렬 검증에 사용할 허브 3개를 준비한다.
    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createQuery("delete from Hub")
                    .executeUpdate();

            insertHub(
                    UUID.fromString("30000000-0000-4000-8000-000000000001"),
                    "서울특별시 센터",
                    "경기라는 단어가 포함된 테스트 주소",
                    LocalDateTime.of(2026, 1, 2, 9, 0)
            );
            insertHub(
                    UUID.fromString("30000000-0000-4000-8000-000000000002"),
                    "경기 북부 센터",
                    "북부 테스트 주소",
                    LocalDateTime.of(2026, 1, 3, 9, 0)
            );
            insertHub(
                    UUID.fromString("30000000-0000-4000-8000-000000000003"),
                    "경기 남부 센터",
                    "남부 테스트 주소",
                    LocalDateTime.of(2026, 1, 1, 9, 0)
            );
        });
    }

    @Test
    @DisplayName("검색어가 이름에 포함된 허브만 반환")
    void searchesHubsByName() throws Exception {
        mockMvc.perform(get("/api/v1/hubs")
                        .header(
                                AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001"
                        )
                        .header(
                                AuthContract.HEADER_USER_ROLE,
                                AuthRole.USER.name()
                        )
                        .param("name", "경기"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder(
                        "경기 북부 센터",
                        "경기 남부 센터"
                )));
    }

    @Test
    @DisplayName("검색어에 해당하는 허브가 미존재 시 빈배열 반환")
    void returnsEmptyListWhenNoHubMatchesName() throws Exception {
        mockMvc.perform(get("/api/v1/hubs")
                        .header(
                                AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001"
                        )
                        .header(
                                AuthContract.HEADER_USER_ROLE,
                                AuthRole.USER.name()
                        )
                        .param("name", "제주"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }


    @Test
    @DisplayName("검색어를 전달하지 않으면 전체 허브 반환")
    void returnsAllHubsWhenNameIsOmitted() throws Exception {
        mockMvc.perform(get("/api/v1/hubs")
                        .header(
                                AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001"
                        )
                        .header(
                                AuthContract.HEADER_USER_ROLE,
                                AuthRole.USER.name()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder(
                        "서울특별시 센터",
                        "경기 북부 센터",
                        "경기 남부 센터"
                )));
    }


    @Test
    @DisplayName("정렬조건을 전달하지 않으면 생성일 내림차순으로 반환")
    void returnsHubsInCreatedAtDescendingOrderByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/hubs")
                        .header(
                                AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001"
                        )
                        .header(
                                AuthContract.HEADER_USER_ROLE,
                                AuthRole.USER.name()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].name", contains(
                        "경기 북부 센터",
                        "서울특별시 센터",
                        "경기 남부 센터"
                )));
    }

    @Test
    @DisplayName("이름 검색에서 논리적으로 삭제 된 허브는 제외")
    void excludeDeletedHubsFromNameSearch() throws Exception {
        markHubAsDeleted("경기 북부 센터");

        mockMvc.perform(get("/api/v1/hubs")
                        .header(
                                AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001"
                        )
                        .header(
                                AuthContract.HEADER_USER_ROLE,
                                AuthRole.USER.name()
                        )
                        .param("name", "경기"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("경기 남부 센터")
                );
    }

    @Test
    @DisplayName("전체 조회에서 논리적으로 삭제된 허브는 제외한다")
    void excludesDeletedHubsFromList() throws Exception {
        markHubAsDeleted("경기 북부 센터");

        mockMvc.perform(get("/api/v1/hubs")
                        .header(
                                AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001"
                        )
                        .header(
                                AuthContract.HEADER_USER_ROLE,
                                AuthRole.USER.name()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder(
                        "서울특별시 센터",
                        "경기 남부 센터"
                )));
    }


    @Test
    @DisplayName("논리적으로 삭제된 허브를 단일 조회하면 404를 반환한다")
    void returnsNotFoundWhenHubIsDeleted() throws Exception {
        markHubAsDeleted("경기 북부 센터");

        mockMvc.perform(get(
                        "/api/v1/hubs/{hubId}",
                        "30000000-0000-4000-8000-000000000002"
                )
                        .header(
                                AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001"
                        )
                        .header(
                                AuthContract.HEADER_USER_ROLE,
                                AuthRole.USER.name()
                        ))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("생성일 오름차순을 요청하면 오래된 허브부터 반환")
    void returnsHubsInCreatedAtAscOrderWhenRequested() throws Exception{
        mockMvc.perform(get("/api/v1/hubs")
                                            .header(
                                                    AuthContract.HEADER_USER_ID,
                                                    "20000000-0000-4000-8000-000000000001"
                                            )
                                            .header(
                                                    AuthContract.HEADER_USER_ROLE,
                                                    AuthRole.USER.name()
                                            )
                                            .param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].name", contains(
                        "경기 남부 센터",
                        "서울특별시 센터",
                        "경기 북부 센터"
                )));
    }

    @Test
    @DisplayName("허브명 오름차순을 요청하면 이름순으로 반환한다")
    void returnsHubsInNameAscendingOrderWhenRequested() throws Exception {
        mockMvc.perform(get("/api/v1/hubs")
                        .header(
                                AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001"
                        )
                        .header(
                                AuthContract.HEADER_USER_ROLE,
                                AuthRole.USER.name()
                        )
                        .param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].name", contains(
                        "경기 남부 센터",
                        "경기 북부 센터",
                        "서울특별시 센터"
                )));
    }

    @Test
    @DisplayName("허브명 내림차순을 요청하면 한국어 이름의 역순으로 반환한다")
    void returnsHubsInNameDescendingOrderWhenRequested() throws Exception {
        mockMvc.perform(get("/api/v1/hubs")
                        .header(AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001")
                        .header(AuthContract.HEADER_USER_ROLE, AuthRole.USER.name())
                        .param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains(
                        "서울특별시 센터",
                        "경기 북부 센터",
                        "경기 남부 센터"
                )));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "name,asc | 경기 남부 센터 | 경기 북부 센터 | 서울특별시 센터",
            "name,desc | 서울특별시 센터 | 경기 북부 센터 | 경기 남부 센터"
    })
    @DisplayName("이름 검색 결과에도 요청한 한국어 정렬을 적용한다")
    void sortsNameSearchInRequestedOrder(String sort, String first, String second, String third)
            throws Exception {
        mockMvc.perform(get("/api/v1/hubs")
                        .header(AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001")
                        .header(AuthContract.HEADER_USER_ROLE, AuthRole.USER.name())
                        .param("name", "센터")
                        .param("sort", sort))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains(first, second, third)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "\\"})
    @DisplayName("검색어의 특수문자는 와일드카드가 아닌 실제 문자로 검색한다")
    void searchesSpecialCharactersLiterally(String character) throws Exception {
        String hubName = "특수" + character + " 센터";
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                insertHub(
                        UUID.fromString("30000000-0000-4000-8000-000000000004"),
                        hubName,
                        "특수문자 검색 테스트 주소",
                        LocalDateTime.of(2026, 1, 4, 9, 0)
                ));

        mockMvc.perform(get("/api/v1/hubs")
                        .header(AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001")
                        .header(AuthContract.HEADER_USER_ROLE, AuthRole.USER.name())
                        .param("name", character)
                        .param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains(hubName)));
    }

    @Test
    @DisplayName("지원하지 않는 정렬 조건이면 400을 반환한다")
    void returnsBadRequestWhenSortIsUnsupported() throws Exception {
        mockMvc.perform(get("/api/v1/hubs")
                        .header(
                                AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001"
                        )
                        .header(
                                AuthContract.HEADER_USER_ROLE,
                                AuthRole.USER.name()
                        )
                        .param("sort", "address,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("생성일이 같으면 ID 오름차순으로 반환한다")
    void ordersHubsByIdWhenCreatedAtIsSame() throws Exception {
        UUID firstId =
                UUID.fromString("30000000-0000-4000-8000-000000000001");
        UUID secondId =
                UUID.fromString("30000000-0000-4000-8000-000000000002");
        UUID thirdId =
                UUID.fromString("30000000-0000-4000-8000-000000000003");

        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 9, 0);

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    entityManager.createQuery("delete from Hub")
                            .executeUpdate();

                    insertHub(
                            thirdId,
                            "경기 남부 센터",
                            "남부 테스트 주소",
                            createdAt
                    );
                    insertHub(
                            firstId,
                            "서울특별시 센터",
                            "서울 테스트 주소",
                            createdAt
                    );
                    insertHub(
                            secondId,
                            "경기 북부 센터",
                            "북부 테스트 주소",
                            createdAt
                    );
                });

        mockMvc.perform(get("/api/v1/hubs")
                        .header(
                                AuthContract.HEADER_USER_ID,
                                "20000000-0000-4000-8000-000000000001"
                        )
                        .header(
                                AuthContract.HEADER_USER_ROLE,
                                AuthRole.USER.name()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(
                        firstId.toString(),
                        secondId.toString(),
                        thirdId.toString()
                )));
    }

    // 지정한 이름의 허브를 논리적으로 삭제된 상태로 만들어 조회 제외 여부를 검증한다.
    private void markHubAsDeleted(String name) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                UPDATE hub.p_hubs
                SET deleted_at = CURRENT_TIMESTAMP,
                    deleted_by =
                        '20000000-0000-4000-8000-000000000001'::uuid
                WHERE name = :name
                """)
                    .setParameter("name", name)
                    .executeUpdate();
        });
    }

    // ID와 생성일을 지정한 테스트용 허브를 현재 트랜잭션 안에서 DB에 저장한다.
    private void insertHub(UUID id, String name, String address, LocalDateTime createdAt) {
        entityManager.createNativeQuery("""
                INSERT INTO hub.p_hubs (id, name, address, created_at)
                VALUES (:id, :name, :address, :createdAt)
                """)
                .setParameter("id", id)
                .setParameter("name", name)
                .setParameter("address", address)
                .setParameter("createdAt", createdAt)
                .executeUpdate();
    }
}
