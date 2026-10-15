package com.user.infrastructure.persistence;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.domain.entity.User;
import com.user.domain.repository.UserRepository;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
import com.user.domain.vo.Username;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL에서만 동작하는 제약·잠금 확인 (H2는 부분 인덱스·FOR UPDATE 동작이 다름).
 * 실행: sh ./gradlew :user-ms:test -PincludeContainer
 */
@Tag("container")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserRepositoryImpl.class, UserSchemaInitializer.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // 동시성 확인을 위해 테스트가 직접 트랜잭션을 연다
class UserRepositoryPostgresTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @AfterEach
    void clean() {
        jdbc.update("delete from auth.p_users");
    }

    TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    static User user(String username, String slackId) {
        return User.signup(UUID.randomUUID(), Username.of(username), "홍길동", "hashed", slackId,
                OrganizationType.COMPANY, "스파르타상사");
    }

    static User master(String username, String slackId) {
        return User.initialMaster(UUID.randomUUID(), Username.of(username), "관리자", "hashed", slackId);
    }

    @Test
    @DisplayName("활성 사용자끼리 같은 Slack ID는 저장할 수 없다 (부분 UNIQUE)")
    void slackIdUniqueAmongActive() {
        tx().executeWithoutResult(s -> userRepository.save(user("user01", "U001")));

        assertThatThrownBy(() -> tx().executeWithoutResult(s -> userRepository.save(user("user02", "U001"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.DUPLICATE_SLACK_ID));
    }

    @Test
    @DisplayName("탈퇴한 사용자의 Slack ID는 다시 쓸 수 있다")
    void slackIdReusableAfterWithdraw() {
        tx().executeWithoutResult(s -> {
            User withdrawn = user("user01", "U001");
            withdrawn.withdraw(withdrawn.getId(), LocalDateTime.now());
            userRepository.save(withdrawn);
        });

        tx().executeWithoutResult(s -> userRepository.save(user("user02", "U001")));

        assertThat(jdbc.queryForObject("select count(*) from auth.p_users where slack_id = 'U001'", Long.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("탈퇴한 ID도 재사용할 수 없다 (일반 UNIQUE)")
    void usernameNeverReused() {
        tx().executeWithoutResult(s -> {
            User withdrawn = user("user01", "U001");
            withdrawn.withdraw(withdrawn.getId(), LocalDateTime.now());
            userRepository.save(withdrawn);
        });

        assertThatThrownBy(() -> tx().executeWithoutResult(s -> userRepository.save(user("user01", "U002"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.DUPLICATE_USERNAME));
    }

    @Test
    @DisplayName("예전 이름의 username UNIQUE(ddl-auto update로 남은 것)에 걸려도 DUPLICATE_USERNAME으로 바꾼다")
    void legacyUsernameConstraint() {
        jdbc.execute("alter table auth.p_users drop constraint uk_p_users_username");
        jdbc.execute("alter table auth.p_users add constraint uk1legacyhibernatename unique (username)");
        try {
            tx().executeWithoutResult(s -> userRepository.save(user("user01", "U001")));

            assertThatThrownBy(() -> tx().executeWithoutResult(s -> userRepository.save(user("user01", "U002"))))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.DUPLICATE_USERNAME));
        } finally {
            jdbc.update("delete from auth.p_users");
            jdbc.execute("alter table auth.p_users drop constraint uk1legacyhibernatename");
            jdbc.execute("alter table auth.p_users add constraint uk_p_users_username unique (username)");
        }
    }

    @Test
    @DisplayName("본사(HQ) 소속은 조직 ID를 가질 수 없다 (CHECK)")
    void hqHasNoOrganizationId() {
        UUID id = UUID.randomUUID();
        tx().executeWithoutResult(s -> userRepository.save(master("master01", "M001")));

        assertThatThrownBy(() -> jdbc.update(
                "update auth.p_users set organization_id = ? where organization_type = 'HQ'", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("허용되지 않은 역할 값은 저장할 수 없다 (CHECK)")
    void roleCheck() {
        tx().executeWithoutResult(s -> userRepository.save(user("user01", "U001")));

        assertThatThrownBy(() -> jdbc.update("update auth.p_users set role = 'ADMIN'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("활성 MASTER 수를 셀 때 행을 잠가, 동시에 들어온 다른 요청은 먼저 요청이 끝난 뒤의 값을 본다")
    void countActiveMastersLocksRows() throws Exception {
        User first = master("master01", "M001");
        User second = master("master02", "M002");
        tx().executeWithoutResult(s -> {
            userRepository.save(first);
            userRepository.save(second);
        });
        AtomicLong secondCount = new AtomicLong(-1);

        // 요청 A: 잠금 조회(2명) → 1초 동안 잡고 있다가 master02 강등 → 커밋
        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> tx().executeWithoutResult(s -> {
            assertThat(userRepository.countActiveMastersForUpdate()).isEqualTo(2);
            sleep(1000);
            User target = userRepository.findActiveById(second.getId()).orElseThrow();
            target.changeRole(UserRole.USER);
        }));
        sleep(200);
        // 요청 B: A가 끝날 때까지 기다렸다가 1명을 본다 (잠금이 없으면 2명을 보고 마지막 MASTER를 지울 수 있음)
        long started = System.nanoTime();
        CompletableFuture<Void> b = CompletableFuture.runAsync(() -> tx().executeWithoutResult(s ->
                secondCount.set(userRepository.countActiveMastersForUpdate())));

        CompletableFuture.allOf(a, b).get(10, TimeUnit.SECONDS);

        assertThat(secondCount.get()).isEqualTo(1);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isGreaterThanOrEqualTo(500);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
