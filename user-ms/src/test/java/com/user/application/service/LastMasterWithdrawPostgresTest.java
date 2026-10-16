package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.DeactivateUserCommand;
import com.user.application.dto.WithdrawCommand;
import com.user.domain.entity.User;
import com.user.domain.repository.UserRepository;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.infrastructure.persistence.UserRepositoryImpl;
import com.user.infrastructure.persistence.UserSchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MASTER 2명이 동시에 탈퇴해도 한 명만 성공한다 (U11 테스트 9). 잠금이 없으면 둘 다 "MASTER 2명"을 보고 0명이 된다.
 * 실행: sh ./gradlew :user-ms:test -PincludeContainer
 */
@Tag("container")
@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserRepositoryImpl.class, UserSchemaInitializer.class, UserCommandService.class, FakePasswordHasher.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LastMasterWithdrawPostgresTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    UserCommandService service;

    @Autowired
    UserRepository userRepository;

    @Autowired
    FakePasswordHasher passwordHasher;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    /** 테스트마다 활성 MASTER 수를 처음부터 센다 */
    @AfterEach
    void clean() {
        jdbc.update("delete from auth.p_users");
    }

    @Test
    @DisplayName("U17: 두 MASTER가 동시에 서로를 비활성화하면 하나만 성공하고 다른 쪽은 LAST_MASTER")
    void mastersDeactivatingEachOther() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        User a = User.initialMaster(UUID.randomUUID(), Username.of("master11"), "관리자", "hashed", "M011");
        User b = User.initialMaster(UUID.randomUUID(), Username.of("master12"), "관리자", "hashed", "M012");
        tx.executeWithoutResult(s -> {
            userRepository.save(a);
            userRepository.save(b);
        });
        AtomicReference<Throwable> secondError = new AtomicReference<>();

        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            service.deactivate(new DeactivateUserCommand(b.getId(), a.getId()));
            sleep(500);
        }));
        sleep(150);
        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> {
            try {
                tx.executeWithoutResult(s -> service.deactivate(new DeactivateUserCommand(a.getId(), b.getId())));
            } catch (Throwable e) {
                secondError.set(e);
            }
        });
        CompletableFuture.allOf(first, second).get(10, TimeUnit.SECONDS);

        assertThat(secondError.get()).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.LAST_MASTER));
        assertThat(jdbc.queryForObject(
                "select count(*) from auth.p_users where role = 'MASTER' and deleted_at is null", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("A가 잠금을 쥔 채 커밋 전이면 B는 기다렸다가 1명을 보고 LAST_MASTER")
    void onlyOneMasterCanLeave() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        User a = User.initialMaster(UUID.randomUUID(), Username.of("master01"), "관리자", passwordHasher.hash("Abcd123!"), "M001");
        User b = User.initialMaster(UUID.randomUUID(), Username.of("master02"), "관리자", passwordHasher.hash("Abcd123!"), "M002");
        tx.executeWithoutResult(s -> {
            userRepository.save(a);
            userRepository.save(b);
        });
        AtomicReference<Throwable> secondError = new AtomicReference<>();

        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            service.withdraw(new WithdrawCommand(a.getId(), "Abcd123!"));
            sleep(500);
        }));
        sleep(150);
        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> {
            try {
                tx.executeWithoutResult(s -> service.withdraw(new WithdrawCommand(b.getId(), "Abcd123!")));
            } catch (Throwable e) {
                secondError.set(e);
            }
        });
        CompletableFuture.allOf(first, second).get(10, TimeUnit.SECONDS);

        assertThat(secondError.get()).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.LAST_MASTER));
        assertThat(jdbc.queryForObject(
                "select count(*) from auth.p_users where role = 'MASTER' and deleted_at is null", Long.class)).isEqualTo(1);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
