package com.user.infrastructure.persistence;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.domain.entity.User;
import com.user.domain.repository.UserRepository;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.Username;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 기본 테스트 DB(H2, PostgreSQL 모드)로 확인한다.
 * 부분 UNIQUE·CHECK·잠금은 PostgreSQL 전용이라 UserRepositoryPostgresTest(@Tag container)에서 확인한다.
 */
@DataJpaTest
@Import(UserRepositoryImpl.class)
class UserRepositoryImplTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    EntityManager em;

    static User user(String username, String slackId) {
        return User.signup(UUID.randomUUID(), Username.of(username), "홍길동", "hashed", slackId,
                OrganizationType.COMPANY, "스파르타상사");
    }

    static User master(String username, String slackId) {
        return User.initialMaster(UUID.randomUUID(), Username.of(username), "관리자", "hashed", slackId);
    }

    void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("저장한 사용자를 ID로 조회한다 (생성 일시 자동 기록)")
    void saveAndFind() {
        User saved = userRepository.save(user("user01", "U001"));
        flushAndClear();

        User found = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getUsername()).isEqualTo("user01");
        assertThat(found.getCreatedBy()).isEqualTo(saved.getId());
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("탈퇴한 사용자는 활성 조회에서 빠지지만 ID 중복 검사에는 걸린다")
    void deletedUser() {
        User user = user("user01", "U001");
        user.withdraw(user.getId(), LocalDateTime.now());
        userRepository.save(user);
        flushAndClear();

        assertThat(userRepository.findActiveByUsername("user01")).isEmpty();
        assertThat(userRepository.findActiveById(user.getId())).isEmpty();
        assertThat(userRepository.findById(user.getId())).isPresent();
        assertThat(userRepository.existsByUsername("user01")).isTrue();
    }

    @Test
    @DisplayName("Slack ID 중복은 활성 사용자끼리만 본다")
    void slackIdAmongActiveUsers() {
        User withdrawn = user("user01", "U001");
        withdrawn.withdraw(withdrawn.getId(), LocalDateTime.now());
        userRepository.save(withdrawn);
        userRepository.save(user("user02", "U002"));
        flushAndClear();

        assertThat(userRepository.existsActiveBySlackId("U001")).isFalse();
        assertThat(userRepository.existsActiveBySlackId("U002")).isTrue();
    }

    @Test
    @DisplayName("활성 MASTER만 센다")
    void countActiveMasters() {
        userRepository.save(master("master01", "M001"));
        User withdrawnMaster = master("master02", "M002");
        withdrawnMaster.withdraw(withdrawnMaster.getId(), LocalDateTime.now());
        userRepository.save(withdrawnMaster);
        userRepository.save(user("user01", "U001"));
        flushAndClear();

        assertThat(userRepository.countActiveMastersForUpdate()).isEqualTo(1);
        assertThat(userRepository.existsActiveMaster()).isTrue();
    }

    @Test
    @DisplayName("활성 MASTER가 없으면 false")
    void noActiveMaster() {
        userRepository.save(user("user01", "U001"));
        flushAndClear();

        assertThat(userRepository.existsActiveMaster()).isFalse();
        assertThat(userRepository.countActiveMastersForUpdate()).isZero();
    }

    @Test
    @DisplayName("중복 확인을 동시에 통과해도 DB UNIQUE가 막고, 409 DUPLICATE_USERNAME으로 바꾼다 (500이 아님)")
    void usernameUniqueViolationIsBusinessError() {
        userRepository.save(user("user01", "U001"));

        assertThatThrownBy(() -> userRepository.save(user("user01", "U002")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.DUPLICATE_USERNAME));
    }
}
