package com.user.application.facade;

import com.common.exception.AuthErrorCode;
import com.common.exception.BusinessException;
import com.user.application.dto.LoginCommand;
import com.user.application.dto.LoginResult;
import com.user.application.dto.TokenPairResult;
import com.user.application.service.TokenService;
import com.user.application.service.UserQueryService;
import com.user.domain.entity.User;
import com.user.domain.token.AccessTokenSpec;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeRefreshTokenParser;
import com.user.fake.FakeTokenIssuer;
import com.user.fake.FakeTokenStore;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 재발급 흐름: Refresh 검증 → DB 최신 사용자 → 새 토큰 (U07 테스트 4~6).
 */
class AuthFacadeReissueTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    private final FakeTokenIssuer tokenIssuer = new FakeTokenIssuer();
    private final FakeTokenStore tokenStore = new FakeTokenStore();
    private final AuthFacade facade = new AuthFacade(
            new UserQueryService(userRepository, passwordHasher),
            new TokenService(tokenIssuer, new FakeRefreshTokenParser(tokenIssuer), tokenStore, Clock.systemUTC()));

    private User savedUser() {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of("user01"), "홍길동",
                passwordHasher.hash("Abcd123!"), "U001", OrganizationType.COMPANY, "스파르타상사"));
    }

    private LoginResult login() {
        return facade.login(new LoginCommand("user01", "Abcd123!"));
    }

    private static void assertRefreshInvalid(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID));
    }

    @Test
    @DisplayName("4. 성공하면 새 Access·Refresh를 돌려주고, 저장된 jti가 새 Refresh의 jti로 바뀐다 (Rotation)")
    void rotates() {
        User user = savedUser();
        LoginResult login = login();

        TokenPairResult reissued = facade.reissue(login.refreshToken());

        assertThat(reissued.accessToken()).isNotEqualTo(login.accessToken());
        assertThat(reissued.refreshToken()).isNotEqualTo(login.refreshToken()).isEqualTo(tokenIssuer.lastRefresh().value());
        assertThat(tokenStore.findRefreshJti(user.getId())).contains(tokenIssuer.lastRefresh().jti());
    }

    @Test
    @DisplayName("이전 Refresh로 다시 재발급하면 401, 그 뒤 새 Refresh도 401 (재사용 탐지로 지워짐)")
    void reuseKillsSession() {
        User user = savedUser();
        LoginResult login = login();
        TokenPairResult reissued = facade.reissue(login.refreshToken());

        assertRefreshInvalid(() -> facade.reissue(login.refreshToken()));
        assertRefreshInvalid(() -> facade.reissue(reissued.refreshToken()));
        assertThat(tokenStore.findRefreshJti(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("5. 새 Access의 role·pcr은 토큰이 아니라 DB 최신값이다 (로그인 뒤 역할 변경)")
    void usesLatestRoleFromDb() {
        User user = savedUser();
        LoginResult login = login();
        user.changeRole(UserRole.MASTER);
        user.requirePasswordChange(passwordHasher.hash("Temp123!"));

        facade.reissue(login.refreshToken());

        assertThat(tokenIssuer.accessSpecs()).last()
                .isEqualTo(new AccessTokenSpec(user.getId(), "MASTER", true));
    }

    @Test
    @DisplayName("6. 탈퇴·비활성화된 사용자는 REFRESH_TOKEN_INVALID (계정 상태를 드러내지 않음), 새 토큰을 발급하지 않는다")
    void inactiveUser() {
        User user = savedUser();
        LoginResult login = login();
        user.withdraw(user.getId());
        int issuedBefore = tokenIssuer.issuedCount();

        assertRefreshInvalid(() -> facade.reissue(login.refreshToken()));
        assertThat(tokenIssuer.issuedCount()).isEqualTo(issuedBefore);
    }
}
