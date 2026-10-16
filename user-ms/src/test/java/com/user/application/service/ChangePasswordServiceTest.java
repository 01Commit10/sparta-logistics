package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.ChangePasswordCommand;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * USR-08 비밀번호 변경 (U10 테스트 1~3).
 */
class ChangePasswordServiceTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    private final UserCommandService service = new UserCommandService(userRepository, passwordHasher);

    private User saved() {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of("user01"), "홍길동",
                passwordHasher.hash("Abcd123!"), "U001", OrganizationType.COMPANY, "스파르타상사"));
    }

    private static void assertError(Runnable action, UserErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("1. 현재 비밀번호가 틀리면 INVALID_CREDENTIALS, 바뀌지 않는다")
    void wrongCurrent() {
        User me = saved();

        assertError(() -> service.changePassword(new ChangePasswordCommand(me.getId(), "Wrong123!", "Newpw123!")),
                UserErrorCode.INVALID_CREDENTIALS);
        assertThat(me.getPasswordHash()).isEqualTo(passwordHasher.hash("Abcd123!"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1!", "abcd1234!", "ABCD1234!", "Abcdefgh!", "Abcd12345", "Abcd123!Abcd123!"})
    @DisplayName("2. 새 비밀번호가 정책을 어기면 INVALID_PASSWORD")
    void policyViolation(String newPassword) {
        User me = saved();

        assertError(() -> service.changePassword(new ChangePasswordCommand(me.getId(), "Abcd123!", newPassword)),
                UserErrorCode.INVALID_PASSWORD);
        assertThat(me.getPasswordHash()).isEqualTo(passwordHasher.hash("Abcd123!"));
    }

    @Test
    @DisplayName("2. 새 비밀번호가 현재와 같으면 SAME_PASSWORD")
    void samePassword() {
        User me = saved();

        assertError(() -> service.changePassword(new ChangePasswordCommand(me.getId(), "Abcd123!", "Abcd123!")),
                UserErrorCode.SAME_PASSWORD);
    }

    @Test
    @DisplayName("3. 성공하면 새 비밀번호의 해시로 바뀌고 비밀번호 변경 필요(pcr)가 풀린다")
    void success() {
        User me = saved();
        me.requirePasswordChange(passwordHasher.hash("Temp123!"));

        service.changePassword(new ChangePasswordCommand(me.getId(), "Temp123!", "Newpw123!"));

        User changed = userRepository.findById(me.getId()).orElseThrow();
        assertThat(changed.getPasswordHash()).isEqualTo(passwordHasher.hash("Newpw123!"));
        assertThat(changed.isPasswordChangeRequired()).isFalse();
    }

    @Test
    @DisplayName("탈퇴한 사용자(토큰은 아직 유효)는 USER_NOT_FOUND")
    void deletedUser() {
        User me = saved();
        me.withdraw(me.getId());

        assertError(() -> service.changePassword(new ChangePasswordCommand(me.getId(), "Abcd123!", "Newpw123!")),
                UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("Command의 toString에 비밀번호 원문이 없다")
    void toStringMasks() {
        assertThat(new ChangePasswordCommand(UUID.randomUUID(), "Abcd123!", "Newpw123!").toString())
                .doesNotContain("Abcd123!").doesNotContain("Newpw123!");
    }
}
