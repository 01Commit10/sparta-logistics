package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.UserResult;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.Username;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * USR-18 사용자 검증(내부): 다른 서비스가 활성 사용자의 존재·배송담당자 여부·Slack ID를 확인한다.
 */
class GetActiveUserTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final UserQueryService service = new UserQueryService(userRepository);

    private User saved(String username, String slackId) {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), "홍길동", "hashed", slackId,
                OrganizationType.HUB, "서울센터"));
    }

    private void assertNotFound(UUID id) {
        assertThatThrownBy(() -> service.getActiveUser(id))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("활성 사용자면 배송담당자 여부와 Slack ID를 담아 돌려준다")
    void active() {
        User user = saved("driver01", "U001");
        user.assignDeliveryManager(true);

        UserResult result = service.getActiveUser(user.getId());

        assertThat(result.id()).isEqualTo(user.getId());
        assertThat(result.name()).isEqualTo("홍길동");
        assertThat(result.role()).isEqualTo("USER");
        assertThat(result.deliveryManager()).isTrue();
        assertThat(result.slackId()).isEqualTo("U001");
        assertThat(result.active()).isTrue();
    }

    @Test
    @DisplayName("탈퇴·비활성화된 사용자는 USER_NOT_FOUND")
    void deleted() {
        User user = saved("user01", "U001");
        user.withdraw(user.getId(), LocalDateTime.now());

        assertNotFound(user.getId());
    }

    @Test
    @DisplayName("없는 사용자는 USER_NOT_FOUND")
    void missing() {
        saved("user01", "U001");

        assertNotFound(UUID.randomUUID());
    }
}
