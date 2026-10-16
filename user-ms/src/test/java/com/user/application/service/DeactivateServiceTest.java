package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.DeactivateUserCommand;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * USR-17 비활성화 (U17 테스트 1~2). 동시 비활성화 잠금은 LastMasterWithdrawPostgresTest에서 확인한다.
 */
class DeactivateServiceTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final UserCommandService service = new UserCommandService(userRepository, new FakePasswordHasher());
    private final User master = userRepository.save(User.initialMaster(UUID.randomUUID(), Username.of("master01"),
            "관리자", "hashed", "U000"));

    private User saved(String username) {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), "김배송", "hashed",
                "U-" + username, OrganizationType.HUB, "서울센터"));
    }

    private User savedMaster(String username) {
        return userRepository.save(User.createByMaster(UUID.randomUUID(), Username.of(username), "관리자", "hashed",
                "U-" + username, OrganizationType.HQ, "본사", UserRole.MASTER, false, master.getId()));
    }

    private static void assertError(Runnable action, UserErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("1. 비활성화하면 삭제 일시와 삭제자(MASTER)가 기록된다. 역할·배송담당 여부는 그대로다")
    void deactivates() {
        User driver = saved("driver01");
        driver.assignDeliveryManager(true);

        service.deactivate(new DeactivateUserCommand(driver.getId(), master.getId()));

        User stored = userRepository.findById(driver.getId()).orElseThrow();
        assertThat(stored.isActive()).isFalse();
        assertThat(stored.getDeletedAt()).isNotNull();
        assertThat(stored.getDeletedBy()).isEqualTo(master.getId());
        assertThat(stored.isDeliveryManager()).isTrue();
        assertThat(stored.getRole()).isEqualTo(UserRole.USER);
        assertThat(userRepository.lockCount()).as("USER 대상은 MASTER 잠금 조회를 하지 않는다").isZero();
    }

    @Test
    @DisplayName("다른 MASTER가 남아 있으면 MASTER도 비활성화할 수 있다 (잠금 조회로 센다)")
    void otherMaster() {
        User other = savedMaster("master02");

        service.deactivate(new DeactivateUserCommand(other.getId(), master.getId()));

        assertThat(other.isActive()).isFalse();
        assertThat(userRepository.lockCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("2. 활성 MASTER가 대상 1명뿐이면 LAST_MASTER, 바뀌지 않는다")
    void lastMaster() {
        User other = savedMaster("master02");
        master.withdraw(master.getId());

        assertError(() -> service.deactivate(new DeactivateUserCommand(other.getId(), master.getId())),
                UserErrorCode.LAST_MASTER);
        assertThat(other.isActive()).isTrue();
    }

    @Test
    @DisplayName("2. 이미 삭제된 사용자는 USER_ALREADY_DELETED, 삭제 기록은 처음 그대로다")
    void alreadyDeleted() {
        User user = saved("user01");
        user.withdraw(user.getId());

        assertError(() -> service.deactivate(new DeactivateUserCommand(user.getId(), master.getId())),
                UserErrorCode.USER_ALREADY_DELETED);
        assertThat(user.getDeletedBy()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("없는 사용자는 USER_NOT_FOUND")
    void unknown() {
        assertError(() -> service.deactivate(new DeactivateUserCommand(UUID.randomUUID(), master.getId())),
                UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("본인은 CANNOT_DEACTIVATE_SELF (탈퇴 API로 비밀번호를 확인해야 한다), 바뀌지 않는다")
    void self() {
        savedMaster("master02");

        assertError(() -> service.deactivate(new DeactivateUserCommand(master.getId(), master.getId())),
                UserErrorCode.CANNOT_DEACTIVATE_SELF);
        assertThat(master.isActive()).isTrue();
    }
}
