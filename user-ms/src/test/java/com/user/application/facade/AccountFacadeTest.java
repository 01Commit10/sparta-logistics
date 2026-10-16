package com.user.application.facade;

import com.common.exception.BusinessException;
import com.user.application.dto.ChangePasswordCommand;
import com.user.application.dto.WithdrawCommand;
import com.user.application.service.TokenService;
import com.user.application.service.UserCommandService;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeRefreshTokenParser;
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
 * 계정 변경 + 토큰 무효화 (U10 테스트 5, U11 테스트 6). 트랜잭션 롤백은 AccountRollbackTest에서 확인한다.
 */
class AccountFacadeTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    private final FakeTokenStore tokenStore = new FakeTokenStore();
    private final AccountFacade facade = new AccountFacade(
            new UserCommandService(userRepository, passwordHasher),
            new TokenService(new FakeTokenIssuer(), new FakeRefreshTokenParser(), tokenStore, Clock.systemUTC()));

    private User loggedIn() {
        User user = userRepository.save(User.signup(UUID.randomUUID(), Username.of("user01"), "홍길동",
                passwordHasher.hash("Abcd123!"), "U001", OrganizationType.COMPANY, "스파르타상사"));
        tokenStore.saveRefresh(user.getId(), "refresh-jti", Duration.ofDays(7));
        return user;
    }

    @Test
    @DisplayName("U10: 비밀번호를 바꾸면 그 사용자의 토큰을 모두 무효로 한다 (revoked-at, Refresh 삭제)")
    void changePasswordRevokes() {
        User me = loggedIn();

        facade.changePassword(new ChangePasswordCommand(me.getId(), "Abcd123!", "Newpw123!"));

        assertThat(tokenStore.revokedOf(me.getId())).isPresent();
        assertThat(tokenStore.refreshOf(me.getId())).isEmpty();
    }

    @Test
    @DisplayName("U10 테스트 5: 현재 비밀번호 불일치·정책 위반이면 토큰을 무효로 하지 않는다")
    void changePasswordFailureKeepsTokens() {
        User me = loggedIn();

        assertThatThrownBy(() -> facade.changePassword(new ChangePasswordCommand(me.getId(), "Wrong123!", "Newpw123!")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> facade.changePassword(new ChangePasswordCommand(me.getId(), "Abcd123!", "short")))
                .isInstanceOf(BusinessException.class);

        assertThat(tokenStore.revokedOf(me.getId())).isEmpty();
        assertThat(tokenStore.refreshOf(me.getId())).isPresent();
    }

    @Test
    @DisplayName("U11 테스트 6: 탈퇴하면 토큰을 모두 무효로 한다")
    void withdrawRevokes() {
        User me = loggedIn();

        facade.withdraw(new WithdrawCommand(me.getId(), "Abcd123!"));

        assertThat(me.isActive()).isFalse();
        assertThat(tokenStore.revokedOf(me.getId())).isPresent();
        assertThat(tokenStore.refreshOf(me.getId())).isEmpty();
    }

    @Test
    @DisplayName("U11 테스트 6: 탈퇴에 실패하면 토큰을 무효로 하지 않는다")
    void withdrawFailureKeepsTokens() {
        User me = loggedIn();

        assertThatThrownBy(() -> facade.withdraw(new WithdrawCommand(me.getId(), "Wrong123!")))
                .isInstanceOf(BusinessException.class);

        assertThat(tokenStore.revokedOf(me.getId())).isEmpty();
        assertThat(tokenStore.refreshOf(me.getId())).isPresent();
    }
}
