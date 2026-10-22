package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.UpdateSlackIdCommand;
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
 * USR-07 내 정보 수정: Slack ID만 바꾼다 (U09 테스트 1~4). 수정자(updatedBy)는 UserAuditingTest·통합 테스트에서 확인한다.
 */
class UpdateSlackIdTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final UserCommandService service = new UserCommandService(userRepository, new FakePasswordHasher(), new FakeTemporaryPasswordGenerator());

    private User saved(String username, String slackId) {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), "홍길동", "hashed", slackId,
                OrganizationType.COMPANY, "스파르타상사"));
    }

    private static void assertError(Runnable action, UserErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("1. Slack ID를 바꾸고 바뀐 정보를 돌려준다. 다른 필드는 그대로다")
    void updates() {
        User me = saved("user01", "U001");

        UserResult result = service.updateSlackId(new UpdateSlackIdCommand(me.getId(), "U999"));

        assertThat(result.slackId()).isEqualTo("U999");
        assertThat(result.name()).isEqualTo("홍길동");
        assertThat(userRepository.findById(me.getId()).orElseThrow().getSlackId()).isEqualTo("U999");
    }

    @Test
    @DisplayName("2. 다른 활성 사용자의 Slack ID면 DUPLICATE_SLACK_ID, 바뀌지 않는다")
    void duplicate() {
        User me = saved("user01", "U001");
        saved("user02", "U002");

        assertError(() -> service.updateSlackId(new UpdateSlackIdCommand(me.getId(), "U002")), UserErrorCode.DUPLICATE_SLACK_ID);
        assertThat(me.getSlackId()).isEqualTo("U001");
    }

    @Test
    @DisplayName("3. 내 지금 Slack ID 그대로 보내도 성공한다 (자기 자신은 중복이 아님)")
    void sameAsMine() {
        User me = saved("user01", "U001");

        assertThat(service.updateSlackId(new UpdateSlackIdCommand(me.getId(), "U001")).slackId()).isEqualTo("U001");
    }

    @Test
    @DisplayName("탈퇴한 사용자가 쓰던 Slack ID는 쓸 수 있다")
    void withdrawnUsersSlackId() {
        User me = saved("user01", "U001");
        User withdrawn = saved("user02", "U002");
        withdrawn.withdraw(withdrawn.getId());

        assertThat(service.updateSlackId(new UpdateSlackIdCommand(me.getId(), "U002")).slackId()).isEqualTo("U002");
    }

    @Test
    @DisplayName("4. 탈퇴한 사용자(토큰은 아직 유효)는 USER_NOT_FOUND")
    void deletedUser() {
        User me = saved("user01", "U001");
        me.withdraw(me.getId());

        assertError(() -> service.updateSlackId(new UpdateSlackIdCommand(me.getId(), "U999")), UserErrorCode.USER_NOT_FOUND);
    }
}
