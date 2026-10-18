package com.user.application.facade;

import com.common.exception.BusinessException;
import com.user.application.dto.ResetPasswordCommand;
import com.user.application.dto.TemporaryPasswordResult;
import com.user.application.service.TokenService;
import com.user.application.service.UserCommandService;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeRefreshTokenParser;
import com.user.fake.FakeTemporaryPasswordGenerator;
import com.user.fake.FakeTokenIssuer;
import com.user.fake.FakeTokenStore;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 비밀번호 초기화 + 대상 토큰 무효화 (U18 테스트 3). 트랜잭션 롤백은 UserAdminIntegrationTest에서 확인한다.
 */
class UserAdminFacadeResetPasswordTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakeTokenStore tokenStore = new FakeTokenStore();
    private final UserAdminFacade facade = new UserAdminFacade(
            new UserCommandService(userRepository, new FakePasswordHasher(), new FakeTemporaryPasswordGenerator()),
            new TokenService(new FakeTokenIssuer(), new FakeRefreshTokenParser(), tokenStore, Clock.systemUTC()));

    private User loggedIn(String username) {
        User user = userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), "홍길동", "hashed",
                "U-" + username, OrganizationType.HUB, "서울센터"));
        tokenStore.saveRefresh(user.getId(), "refresh-" + username, Duration.ofDays(7));
        return user;
    }

    @Test
    @DisplayName("3. 성공하면 임시 비밀번호를 돌려주고 대상의 토큰만 무효로 한다. MASTER 토큰은 그대로다")
    void revokesTarget() {
        User target = loggedIn("user01");
        tokenStore.saveRefresh(MASTER_ID, "refresh-master", Duration.ofDays(7));

        TemporaryPasswordResult result = facade.resetPassword(new ResetPasswordCommand(target.getId(), MASTER_ID));

        assertThat(result.temporaryPassword()).isEqualTo(FakeTemporaryPasswordGenerator.TEMPORARY);
        assertThat(tokenStore.revokedOf(target.getId())).isPresent();
        assertThat(tokenStore.refreshOf(target.getId())).isEmpty();
        assertThat(tokenStore.revokedOf(MASTER_ID)).isEmpty();
        assertThat(tokenStore.refreshOf(MASTER_ID)).isPresent();
    }

    @Test
    @DisplayName("실패(본인·없음)하면 토큰을 건드리지 않는다")
    void failureKeepsTokens() {
        tokenStore.saveRefresh(MASTER_ID, "refresh-master", Duration.ofDays(7));

        assertThatThrownBy(() -> facade.resetPassword(new ResetPasswordCommand(MASTER_ID, MASTER_ID)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> facade.resetPassword(new ResetPasswordCommand(UUID.randomUUID(), MASTER_ID)))
                .isInstanceOf(BusinessException.class);

        assertThat(tokenStore.revokedOf(MASTER_ID)).isEmpty();
        assertThat(tokenStore.refreshOf(MASTER_ID)).isPresent();
    }
}
