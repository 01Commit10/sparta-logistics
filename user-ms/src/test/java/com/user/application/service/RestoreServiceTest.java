package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.UserResult;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
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
 * USR-22 재활성화 (U19 테스트 1~2). 비밀번호·역할·배송담당 여부는 그대로 둔다.
 */
class RestoreServiceTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final UserCommandService service = new UserCommandService(userRepository, new FakePasswordHasher(),
            new FakeTemporaryPasswordGenerator());

    private User saved(String username, String slackId) {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), "홍길동", "hashed:Abcd123!",
                slackId, OrganizationType.HUB, "서울센터"));
    }

    private static void assertError(Runnable action, UserErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("1. MASTER가 비활성화한 사용자 → 활성, 삭제 일시·삭제자 초기화, 나머지 정보 그대로")
    void restoreDeactivated() {
        User user = userRepository.save(User.createByMaster(UUID.randomUUID(), Username.of("driver01"), "김배송",
                "hashed:Abcd123!", "U001", OrganizationType.HUB, "서울센터", UserRole.USER, true, MASTER_ID));
        user.withdraw(MASTER_ID);

        UserResult result = service.restore(user.getId());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isTrue();
        assertThat(reloaded.getDeletedAt()).isNull();
        assertThat(reloaded.getDeletedBy()).isNull();
        assertThat(reloaded.getPasswordHash()).isEqualTo("hashed:Abcd123!");
        assertThat(result.active()).isTrue();
        assertThat(result.deletedAt()).isNull();
        assertThat(result.deliveryManager()).isTrue();
        assertThat(result.role()).isEqualTo("USER");
        assertThat(result.passwordChangeRequired()).isTrue();
    }

    @Test
    @DisplayName("1. 본인이 탈퇴한 사용자도 재활성화한다 (07 서비스 정책)")
    void restoreWithdrawn() {
        User user = saved("user01", "U001");
        user.withdraw(user.getId());

        assertThat(service.restore(user.getId()).active()).isTrue();
        assertThat(user.isPasswordChangeRequired()).isFalse();
    }

    @Test
    @DisplayName("2. 활성 사용자면 USER_NOT_DELETED")
    void active() {
        User user = saved("user01", "U001");

        assertError(() -> service.restore(user.getId()), UserErrorCode.USER_NOT_DELETED);
    }

    @Test
    @DisplayName("2. 그사이 다른 활성 사용자가 같은 slackId를 쓰면 DUPLICATE_SLACK_ID, 삭제 상태 그대로")
    void slackIdTaken() {
        User gone = saved("gone01", "U001");
        gone.withdraw(gone.getId());
        saved("user02", "U001");

        assertError(() -> service.restore(gone.getId()), UserErrorCode.DUPLICATE_SLACK_ID);
        assertThat(gone.isActive()).isFalse();
    }

    @Test
    @DisplayName("2. 같은 slackId를 쓰던 다른 사용자가 삭제 상태면 재활성화된다")
    void slackIdOnlyOnDeleted() {
        User gone = saved("gone01", "U001");
        gone.withdraw(gone.getId());
        User other = saved("gone02", "U001");
        other.withdraw(other.getId());

        assertThat(service.restore(gone.getId()).active()).isTrue();
    }

    @Test
    @DisplayName("2. 없으면 USER_NOT_FOUND")
    void notFound() {
        assertError(() -> service.restore(UUID.randomUUID()), UserErrorCode.USER_NOT_FOUND);
    }
}
