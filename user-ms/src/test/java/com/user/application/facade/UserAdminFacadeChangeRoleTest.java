package com.user.application.facade;

import com.common.exception.BusinessException;
import com.user.application.dto.ChangeRoleCommand;
import com.user.application.dto.UserResult;
import com.user.application.service.TokenService;
import com.user.application.service.UserCommandService;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeRefreshTokenParser;
import com.user.fake.FakeTokenIssuer;
import com.user.fake.FakeTokenStore;
import com.user.fake.FakeTemporaryPasswordGenerator;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 역할 변경 + 토큰 무효화 (U15 테스트 5).
 */
class UserAdminFacadeChangeRoleTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakeTokenStore tokenStore = new FakeTokenStore();
    private final UserAdminFacade facade = new UserAdminFacade(
            new UserCommandService(userRepository, new FakePasswordHasher(), new FakeTemporaryPasswordGenerator()),
            new TokenService(new FakeTokenIssuer(), new FakeRefreshTokenParser(), tokenStore, Clock.systemUTC()));

    private User loggedIn() {
        User user = userRepository.save(User.signup(UUID.randomUUID(), Username.of("user01"), "홍길동", "hashed",
                "U001", OrganizationType.HUB, "서울센터"));
        tokenStore.saveRefresh(user.getId(), "refresh-jti", Duration.ofDays(7));
        return user;
    }

    @Test
    @DisplayName("5. 역할이 실제로 바뀌면 대상의 토큰을 무효로 하고 바뀐 정보를 돌려준다")
    void changedRevokes() {
        User user = loggedIn();

        UserResult result = facade.changeRole(new ChangeRoleCommand(user.getId(), "MASTER"));

        assertThat(result.role()).isEqualTo("MASTER");
        assertThat(tokenStore.revokedOf(user.getId())).isPresent();
        assertThat(tokenStore.refreshOf(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("5. 같은 역할이면 토큰을 건드리지 않는다")
    void unchangedKeepsTokens() {
        User user = loggedIn();

        UserResult result = facade.changeRole(new ChangeRoleCommand(user.getId(), "USER"));

        assertThat(result.role()).isEqualTo("USER");
        assertThat(tokenStore.revokedOf(user.getId())).isEmpty();
        assertThat(tokenStore.refreshOf(user.getId())).isPresent();
    }

    @Test
    @DisplayName("실패(없는 사용자)하면 토큰을 건드리지 않는다")
    void failureKeepsTokens() {
        User user = loggedIn();
        user.withdraw(user.getId());

        assertThatThrownBy(() -> facade.changeRole(new ChangeRoleCommand(user.getId(), "MASTER")))
                .isInstanceOf(BusinessException.class);
        assertThat(tokenStore.revokedOf(user.getId())).isEmpty();
    }
}
