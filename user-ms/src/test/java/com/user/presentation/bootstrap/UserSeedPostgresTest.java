package com.user.presentation.bootstrap;

import com.common.auth.AuthContract;
import com.user.domain.service.PasswordHasher;
import com.user.domain.vo.Username;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U23 사용자 시드 (db/seed/users.sql)를 실제 PostgreSQL에 넣고 확인한다. 테스트 프로필은 시드를 끄므로 여기서만 켠다.
 * 실행: sh ./gradlew :user-ms:test -PincludeContainer
 */
@Tag("container")
@Testcontainers
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=create", "spring.sql.init.mode=always"})
@AutoConfigureMockMvc
class UserSeedPostgresTest {

    private static final String SEED_PASSWORD = "Seed1234!";
    private static final String SEOUL = "10000000-0000-4000-8000-000000000001";
    private static final String BUSAN = "10000000-0000-4000-8000-000000000004";
    /** 시드 행만 (.env에 초기 MASTER 값이 있으면 USR-19가 MASTER 1명을 더 만든다) */
    private static final String SEED_ONLY = "(id::text like 'a0000000-%' or id::text like 'b0000000-%')";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @Autowired
    PasswordHasher passwordHasher;

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("기동 시 배송담당자 34명(허브 17곳 × 2명, 소속 연결)과 업체 사용자 4명이 들어간다")
    void seeded() {
        assertThat(jdbc.queryForObject("select count(*) from auth.p_users where is_delivery_manager", Long.class))
                .isEqualTo(34);
        List<Map<String, Object>> perHub = jdbc.queryForList("""
                select organization_id, count(*) as cnt from auth.p_users
                where is_delivery_manager and organization_type = 'HUB' and deleted_at is null
                group by organization_id""");
        assertThat(perHub).hasSize(17).allSatisfy(row -> assertThat(row.get("cnt")).isEqualTo(2L));
        assertThat(jdbc.queryForObject(
                "select count(*) from auth.p_users where organization_type = 'COMPANY' and not is_delivery_manager",
                Long.class)).isEqualTo(4);
    }

    @Test
    @DisplayName("시드 사용자는 USER, 비밀번호 Seed1234!, 변경 강제 없음, 작성자 시스템, 아이디는 가입 규칙을 통과한다")
    void fields() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select username, role, password_hash, password_change_required, created_by from auth.p_users where "
                        + SEED_ONLY);
        assertThat(rows).hasSize(38).allSatisfy(row -> {
            assertThat(row.get("role")).isEqualTo("USER");
            assertThat(passwordHasher.matches(SEED_PASSWORD, (String) row.get("password_hash"))).isTrue();
            assertThat(row.get("password_change_required")).isEqualTo(false);
            assertThat(row.get("created_by")).isEqualTo(AuthContract.SYSTEM_USER_ID);
            assertThat(Username.of((String) row.get("username")).value()).isEqualTo(row.get("username"));
        });
    }

    @Test
    @DisplayName("다시 실행해도 행이 늘지 않는다 (재기동해도 중복 없음)")
    void idempotent() {
        new ResourceDatabasePopulator(new ClassPathResource("db/seed/users.sql")).execute(dataSource);

        assertThat(jdbc.queryForObject("select count(*) from auth.p_users where " + SEED_ONLY, Long.class))
                .isEqualTo(38);
    }

    @Test
    @DisplayName("시드만으로 배송담당자 무작위 배정(USR-23)이 된다: 서울 → 부산")
    void randomAssignment() throws Exception {
        mockMvc.perform(get("/internal/v1/users/delivery-managers/random")
                        .param("departureHubId", SEOUL).param("arrivalHubId", BUSAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departureManager.hubId").value(SEOUL))
                .andExpect(jsonPath("$.departureManager.name").value(startsWith("서울 배송담당자")))
                .andExpect(jsonPath("$.arrivalManager.hubId").value(BUSAN))
                .andExpect(jsonPath("$.arrivalManager.name").value(startsWith("부산 배송담당자")));
    }
}
