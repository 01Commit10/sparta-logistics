package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.AssignDeliveryManagerCommand;
import com.user.application.dto.UserResult;
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
 * USR-15 배송담당자 지정·해제 (U16 테스트 1~2). 수정자(updatedBy)는 UserAdminIntegrationTest에서 확인한다.
 */
class AssignDeliveryManagerTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final UserCommandService service = new UserCommandService(userRepository, new FakePasswordHasher(), new FakeTemporaryPasswordGenerator());

    private User saved(String username, OrganizationType organizationType) {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), "김배송", "hashed",
                "U-" + username, organizationType, "서울센터"));
    }

    private static void assertError(Runnable action, UserErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("1. 허브 소속 사용자를 지정하면 저장되고 바뀐 정보를 돌려준다")
    void assign() {
        User user = saved("driver01", OrganizationType.HUB);

        UserResult result = service.assignDeliveryManager(new AssignDeliveryManagerCommand(user.getId(), true));

        assertThat(result.id()).isEqualTo(user.getId());
        assertThat(result.deliveryManager()).isTrue();
        assertThat(userRepository.findById(user.getId()).orElseThrow().isDeliveryManager()).isTrue();
    }

    @Test
    @DisplayName("1. 지정된 사용자를 해제한다")
    void unassign() {
        User user = saved("driver01", OrganizationType.HUB);
        user.assignDeliveryManager(true);

        UserResult result = service.assignDeliveryManager(new AssignDeliveryManagerCommand(user.getId(), false));

        assertThat(result.deliveryManager()).isFalse();
        assertThat(userRepository.findById(user.getId()).orElseThrow().isDeliveryManager()).isFalse();
    }

    @Test
    @DisplayName("업체 소속은 409 DELIVERY_MANAGER_REQUIRES_HUB, 바뀌지 않는다")
    void companyUser() {
        User user = saved("staff01", OrganizationType.COMPANY);

        assertError(() -> service.assignDeliveryManager(new AssignDeliveryManagerCommand(user.getId(), true)),
                UserErrorCode.DELIVERY_MANAGER_REQUIRES_HUB);
        assertThat(user.isDeliveryManager()).isFalse();
    }

    @Test
    @DisplayName("2. 삭제(탈퇴·비활성화)된 사용자는 USER_NOT_FOUND, 바뀌지 않는다")
    void deletedUser() {
        User user = saved("driver01", OrganizationType.HUB);
        user.withdraw(user.getId());

        assertError(() -> service.assignDeliveryManager(new AssignDeliveryManagerCommand(user.getId(), true)),
                UserErrorCode.USER_NOT_FOUND);
        assertThat(user.isDeliveryManager()).isFalse();
    }

    @Test
    @DisplayName("2. 없는 사용자는 USER_NOT_FOUND")
    void unknownUser() {
        assertError(() -> service.assignDeliveryManager(new AssignDeliveryManagerCommand(UUID.randomUUID(), true)),
                UserErrorCode.USER_NOT_FOUND);
    }
}
