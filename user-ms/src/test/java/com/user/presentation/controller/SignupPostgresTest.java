package com.user.presentation.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 PostgreSQL 제약(username UNIQUE, 활성 사용자 slack_id 부분 UNIQUE)에서 가입 API가 409로 응답하는지 확인한다.
 * 동시에 들어온 가입은 중복 확인(exists)을 함께 통과하므로 DB 제약이 막고, 500이 아니라 409가 나와야 한다.
 * 실행: sh ./gradlew :user-ms:test -PincludeContainer
 */
@Tag("container")
@Testcontainers
// 컨테이너가 컨텍스트보다 먼저 멈추므로 종료 시 drop(create-drop)을 하지 않는다 (30초 연결 대기 방지)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@AutoConfigureMockMvc
class SignupPostgresTest {

    private static final int CONCURRENT_REQUESTS = 5;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.update("delete from auth.p_users");
    }

    private static String body(String username, String slackId) {
        return """
                { "username": "%s", "password": "Abcd123!", "name": "홍길동", "slackId": "%s",
                  "organizationType": "HUB", "organizationName": "경기남부허브" }
                """.formatted(username, slackId);
    }

    private MockHttpServletResponse signup(String username, String slackId) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(body(username, slackId)))
                .andReturn().getResponse();
    }

    /** 요청을 한꺼번에 출발시키고, 상태 코드별 개수와 409 응답의 오류 코드를 모은다. */
    private Map<Integer, Long> signupConcurrently(IntFunction<String[]> usernameAndSlackId,
                                                  List<String> conflictBodies) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MockHttpServletResponse>> futures = IntStream.range(0, CONCURRENT_REQUESTS)
                    .mapToObj(i -> pool.submit(() -> {
                        String[] values = usernameAndSlackId.apply(i);
                        start.await();
                        return signup(values[0], values[1]);
                    }))
                    .toList();
            start.countDown();

            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            for (MockHttpServletResponse response : responses) {
                if (response.getStatus() == 409) {
                    conflictBodies.add(response.getContentAsString());
                }
            }
            return responses.stream().collect(Collectors.groupingBy(MockHttpServletResponse::getStatus, Collectors.counting()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("같은 username으로 동시에 가입하면 1건만 201이고 나머지는 409 DUPLICATE_USERNAME (500 없음)")
    void concurrentSameUsername() throws Exception {
        List<String> conflicts = new ArrayList<>();

        Map<Integer, Long> statuses = signupConcurrently(i -> new String[]{"race01", "URACE0" + i}, conflicts);

        assertThat(statuses).containsExactlyInAnyOrderEntriesOf(Map.of(201, 1L, 409, (long) CONCURRENT_REQUESTS - 1));
        assertThat(conflicts).allSatisfy(b -> assertThat(b).contains("\"code\":\"DUPLICATE_USERNAME\""));
        assertThat(jdbc.queryForObject("select count(*) from auth.p_users where username = 'race01'", Long.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("같은 Slack ID로 동시에 가입하면 1건만 201이고 나머지는 409 DUPLICATE_SLACK_ID (부분 UNIQUE)")
    void concurrentSameSlackId() throws Exception {
        List<String> conflicts = new ArrayList<>();

        Map<Integer, Long> statuses = signupConcurrently(i -> new String[]{"slack0" + i, "URACESLACK"}, conflicts);

        assertThat(statuses).containsExactlyInAnyOrderEntriesOf(Map.of(201, 1L, 409, (long) CONCURRENT_REQUESTS - 1));
        assertThat(conflicts).allSatisfy(b -> assertThat(b).contains("\"code\":\"DUPLICATE_SLACK_ID\""));
        assertThat(jdbc.queryForObject("select count(*) from auth.p_users where slack_id = 'URACESLACK'", Long.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴한 사용자의 Slack ID로는 다시 가입할 수 있고, 탈퇴한 username으로는 가입할 수 없다")
    void reuseAfterWithdraw() throws Exception {
        assertThat(signup("old001", "UREUSE01").getStatus()).isEqualTo(201);
        jdbc.update("update auth.p_users set deleted_at = now() where username = 'old001'");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(body("new001", "UREUSE01")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(body("old001", "UREUSE02")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));

        assertThat(jdbc.queryForObject("select count(*) from auth.p_users where slack_id = 'UREUSE01'", Long.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from auth.p_users where slack_id = 'UREUSE02'", Long.class))
                .isZero();
    }

    @Test
    @DisplayName("PostgreSQL에 저장된 값: BCrypt 해시, role USER, created_by = 본인, organization_id 없음")
    void storedRow() throws Exception {
        assertThat(signup("row001", "UROW01").getStatus()).isEqualTo(201);

        Map<String, Object> row = jdbc.queryForMap(
                "select id, role, password_hash, created_by, organization_type, organization_id, is_delivery_manager "
                        + "from auth.p_users where username = 'row001'");
        assertThat(row.get("role")).isEqualTo("USER");
        assertThat((String) row.get("password_hash")).startsWith("$2").doesNotContain("Abcd123!");
        assertThat(row.get("created_by")).isEqualTo(row.get("id"));
        assertThat(row.get("organization_type")).isEqualTo("HUB");
        assertThat(row.get("organization_id")).isNull();
        assertThat(row.get("is_delivery_manager")).isEqualTo(false);
    }
}
