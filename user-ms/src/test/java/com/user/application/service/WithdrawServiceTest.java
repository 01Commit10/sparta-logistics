package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.WithdrawCommand;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeTemporaryPasswordGenerator;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * USR-09 회원탈퇴 (U11 테스트 1~5). 삭제 시각은 BaseEntity가 기록한다.
 */
class WithdrawServiceTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    private final UserCommandService service = new UserCommandService(userRepository, passwordHasher, new FakeTemporaryPasswordGenerator());

    private User user(String username, String slackId) {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), "홍길동",
                passwordHasher.hash("Abcd123!"), slackId, OrganizationType.COMPANY, "스파르타상사"));
    }

    private User master(String username, String slackId) {
        return userRepository.save(User.initialMaster(UUID.randomUUID(), Username.of(username), "관리자",
                passwordHasher.hash("Abcd123!"), slackId));
    }

    private static void assertError(Runnable action, UserErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("1. 비밀번호가 틀리면 INVALID_CREDENTIALS, 탈퇴하지 않는다")
    void wrongPassword() {
        User me = user("user01", "U001");

        assertError(() -> service.withdraw(new WithdrawCommand(me.getId(), "Wrong123!")), UserErrorCode.INVALID_CREDENTIALS);
        assertThat(me.isActive()).isTrue();
    }

    @Test
    @DisplayName("2. 성공하면 논리 삭제되고 삭제자는 본인이다")
    void success() {
        User me = user("user01", "U001");

        service.withdraw(new WithdrawCommand(me.getId(), "Abcd123!"));

        User withdrawn = userRepository.findById(me.getId()).orElseThrow();
        assertThat(withdrawn.isActive()).isFalse();
        assertThat(withdrawn.getDeletedAt()).isNotNull();
        assertThat(withdrawn.getDeletedBy()).isEqualTo(me.getId());
    }

    @Test
    @DisplayName("3. 활성 MASTER가 나 하나면 LAST_MASTER, 탈퇴하지 않는다")
    void lastMaster() {
        User me = master("master01", "M001");

        assertError(() -> service.withdraw(new WithdrawCommand(me.getId(), "Abcd123!")), UserErrorCode.LAST_MASTER);
        assertThat(me.isActive()).isTrue();
    }

    @Test
    @DisplayName("4. 다른 활성 MASTER가 있으면 MASTER도 탈퇴할 수 있다 (잠금 조회로 센다)")
    void oneOfTwoMasters() {
        User me = master("master01", "M001");
        master("master02", "M002");

        service.withdraw(new WithdrawCommand(me.getId(), "Abcd123!"));

        assertThat(me.isActive()).isFalse();
        assertThat(userRepository.lockCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("5. USER 탈퇴에는 MASTER 잠금 조회를 하지 않는다")
    void userDoesNotLock() {
        User me = user("user01", "U001");

        service.withdraw(new WithdrawCommand(me.getId(), "Abcd123!"));

        assertThat(me.isActive()).isFalse();
        assertThat(userRepository.lockCount()).isZero();
    }

    @Test
    @DisplayName("이미 탈퇴한 사용자(토큰은 아직 유효)는 USER_NOT_FOUND")
    void alreadyWithdrawn() {
        User me = user("user01", "U001");
        me.withdraw(me.getId());

        assertError(() -> service.withdraw(new WithdrawCommand(me.getId(), "Abcd123!")), UserErrorCode.USER_NOT_FOUND);
    }
}
