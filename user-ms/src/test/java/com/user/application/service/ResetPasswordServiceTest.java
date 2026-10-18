package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.ResetPasswordCommand;
import com.user.application.dto.TemporaryPasswordResult;
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
 * USR-21 비밀번호 초기화 (U18 테스트 2). 고정 생성기로 원문·해시를 확인한다.
 */
class ResetPasswordServiceTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakeTemporaryPasswordGenerator generator = new FakeTemporaryPasswordGenerator();
    private final UserCommandService service = new UserCommandService(userRepository, new FakePasswordHasher(), generator);

    private User saved(String username) {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), "홍길동", "hashed:Abcd123!",
                "U-" + username, OrganizationType.HUB, "서울센터"));
    }

    private static void assertError(Runnable action, UserErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("2. 임시 비밀번호의 해시로 바꾸고 변경 필요로 표시한다. 결과에 원문과 대상 id·username이 있다")
    void reset() {
        User user = saved("user01");

        TemporaryPasswordResult result = service.resetPassword(new ResetPasswordCommand(user.getId(), MASTER_ID));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getPasswordHash()).isEqualTo("hashed:" + FakeTemporaryPasswordGenerator.TEMPORARY);
        assertThat(reloaded.isPasswordChangeRequired()).isTrue();
        assertThat(result.userId()).isEqualTo(user.getId());
        assertThat(result.username()).isEqualTo("user01");
        assertThat(result.temporaryPassword()).isEqualTo(FakeTemporaryPasswordGenerator.TEMPORARY);
        assertThat(generator.count()).isEqualTo(1);
        assertThat(result.toString()).doesNotContain(FakeTemporaryPasswordGenerator.TEMPORARY);
    }

    @Test
    @DisplayName("2. 다른 MASTER도 초기화할 수 있다")
    void otherMaster() {
        User other = userRepository.save(User.initialMaster(UUID.randomUUID(), Username.of("master02"), "관리자",
                "hashed:Abcd123!", "M002"));

        service.resetPassword(new ResetPasswordCommand(other.getId(), MASTER_ID));

        assertThat(other.isPasswordChangeRequired()).isTrue();
    }

    @Test
    @DisplayName("2. 본인이면 CANNOT_RESET_OWN_PASSWORD, 생성하지도 바꾸지도 않는다")
    void self() {
        User master = userRepository.save(User.initialMaster(MASTER_ID, Username.of("master01"), "관리자",
                "hashed:Abcd123!", "M001"));

        assertError(() -> service.resetPassword(new ResetPasswordCommand(MASTER_ID, MASTER_ID)),
                UserErrorCode.CANNOT_RESET_OWN_PASSWORD);
        assertThat(master.getPasswordHash()).isEqualTo("hashed:Abcd123!");
        assertThat(master.isPasswordChangeRequired()).isFalse();
        assertThat(generator.count()).isZero();
    }

    @Test
    @DisplayName("2. 없거나 삭제된 사용자면 USER_NOT_FOUND, 바꾸지 않는다")
    void notFound() {
        User withdrawn = saved("gone01");
        withdrawn.withdraw(withdrawn.getId());

        assertError(() -> service.resetPassword(new ResetPasswordCommand(UUID.randomUUID(), MASTER_ID)),
                UserErrorCode.USER_NOT_FOUND);
        assertError(() -> service.resetPassword(new ResetPasswordCommand(withdrawn.getId(), MASTER_ID)),
                UserErrorCode.USER_NOT_FOUND);
        assertThat(withdrawn.getPasswordHash()).isEqualTo("hashed:Abcd123!");
        assertThat(withdrawn.isPasswordChangeRequired()).isFalse();
    }
}
