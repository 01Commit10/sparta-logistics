package com.user.presentation.bootstrap;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.InitialMasterCommand;
import com.user.application.dto.InitialMasterResult;
import com.user.application.service.UserCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 PostgreSQL 스키마(CHECK: HQ는 organization_id 없음, role 값, 부분 UNIQUE)에서 초기 MASTER 생성을 확인한다.
 * 각 테스트는 빈 DB에서 기동한 직후 상태로 시작한다.
 * 실행: sh ./gradlew :user-ms:test -PincludeContainer
 */
@Tag("container")
@Testcontainers
// 컨테이너가 컨텍스트보다 먼저 멈추므로 종료 시 drop(create-drop)을 하지 않는다 (30초 연결 대기 방지)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "initial-master.username=master01",
        "initial-master.password=Master!2345",
        "initial-master.name=관리자",
        "initial-master.slack-id=U00MASTER01"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)   // 테스트마다 다시 기동 → 다시 생성
class InitialMasterPostgresTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InitialMasterInitializer initializer;

    @Autowired
    UserCommandService userCommandService;

    private long activeMasters() {
        return jdbc.queryForObject(
                "select count(*) from auth.p_users where role = 'MASTER' and deleted_at is null", Long.class);
    }

    @Test
    @DisplayName("기동하면 PostgreSQL 제약을 통과한 MASTER 1행이 생긴다 (HQ·조직 ID 없음·created_by 없음·BCrypt)")
    void createdOnStartup() {
        Map<String, Object> row = jdbc.queryForMap("""
                select role, organization_type, organization_id, organization_name, created_by, password_hash,
                       is_delivery_manager, deleted_at
                from auth.p_users where username = 'master01'
                """);

        assertThat(row.get("role")).isEqualTo("MASTER");
        assertThat(row.get("organization_type")).isEqualTo("HQ");
        assertThat(row.get("organization_id")).isNull();
        assertThat(row.get("organization_name")).isEqualTo("본사");
        assertThat(row.get("created_by")).isNull();
        assertThat((String) row.get("password_hash")).startsWith("$2").doesNotContain("Master!2345");
        assertThat(row.get("is_delivery_manager")).isEqualTo(false);
        assertThat(row.get("deleted_at")).isNull();
        assertThat(activeMasters()).isEqualTo(1);
    }

    @Test
    @DisplayName("기동 훅을 다시 실행해도(재기동) MASTER는 1명 그대로다")
    void idempotentOnRestart() {
        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());

        assertThat(jdbc.queryForObject("select count(*) from auth.p_users", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("MASTER가 모두 탈퇴하면 새 ID로 다시 만들 수 있고, 탈퇴한 MASTER의 Slack ID도 다시 쓸 수 있다 (부분 UNIQUE)")
    void recreatedAfterWithdraw() {
        jdbc.update("update auth.p_users set deleted_at = now() where username = 'master01'");

        InitialMasterResult result = userCommandService.createInitialMasterIfAbsent(
                new InitialMasterCommand("master02", "Master!2345", "관리자2", "U00MASTER01"));

        assertThat(result).isEqualTo(InitialMasterResult.CREATED);
        assertThat(activeMasters()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from auth.p_users where slack_id = 'U00MASTER01'", Long.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("탈퇴한 MASTER와 같은 ID로는 만들지 않고 DUPLICATE_USERNAME으로 실패한다 (행이 늘지 않음)")
    void sameUsernameAsWithdrawn() {
        jdbc.update("update auth.p_users set deleted_at = now() where username = 'master01'");

        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.DUPLICATE_USERNAME));
        assertThat(jdbc.queryForObject("select count(*) from auth.p_users", Long.class)).isEqualTo(1);
        assertThat(activeMasters()).isZero();
    }
}
