package com.user.application.facade;

import com.common.exception.AuthErrorCode;
import com.common.exception.BusinessException;
import com.common.exception.ErrorCode;
import com.common.exception.UserErrorCode;
import com.user.application.dto.LoginCommand;
import com.user.application.dto.LoginResult;
import com.user.application.service.TokenService;
import com.user.application.service.UserQueryService;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 리뷰: 로그인·재발급 도중 무효화(비밀번호 변경·탈퇴·역할 변경·비활성화·초기화)가 끼어들면 그 세션을 내주지 않는다.
 * 발급 전에 읽은 revoked-at(기준값)보다 새 값이 크면 무효화가 지나간 것이다. 발급 전이면 만들지 않고, 저장 뒤면 내 Refresh만 지운다.
 * 무효화가 저장 뒤에 오면 그 무효화가 Refresh를 지우고 Access도 막는다 (RevocationInvariantTest).
 * 실제 서버의 동시 요청 확인은 scratchpad e2e_race.py.
 */
class AuthIssueRaceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T10:00:00.500Z"), ZoneOffset.UTC);

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    private final FakeTokenIssuer tokenIssuer = new FakeTokenIssuer();
    private final FakeTokenStore tokenStore = new FakeTokenStore();
    private final TokenService tokenService = new TokenService(tokenIssuer, new FakeRefreshTokenParser(tokenIssuer), tokenStore, CLOCK);
    private final AuthFacade facade = new AuthFacade(new UserQueryService(userRepository), tokenService, passwordHasher);

    private User savedUser() {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of("user01"), "홍길동",
                passwordHasher.hash("Abcd123!"), "U001", OrganizationType.COMPANY, "스파르타상사"));
    }

    private LoginResult login() {
        return facade.login(new LoginCommand("user01", "Abcd123!"));
    }

    private static void assertFails(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test
    @DisplayName("로그인: 비밀번호를 비교하는 동안 무효화되면 INVALID_CREDENTIALS, 토큰을 만들지도 저장하지도 않는다")
    void login_revokedDuringPasswordCheck() {
        User user = savedUser();
        passwordHasher.duringNextMatches(() -> tokenService.revokeAll(user.getId()));

        assertFails(this::login, UserErrorCode.INVALID_CREDENTIALS);
        assertThat(tokenIssuer.issuedCount()).isZero();
        assertThat(tokenStore.refreshOf(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("로그인: 해시를 읽은 직후 무효화돼도 막는다 (기준값은 해시를 읽기 전에 읽는다)")
    void login_revokedRightAfterCredentialRead() {
        User user = savedUser();
        userRepository.afterNextFindActiveById(() -> tokenService.revokeAll(user.getId()));

        assertFails(this::login, UserErrorCode.INVALID_CREDENTIALS);
        assertThat(tokenStore.refreshOf(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("로그인: Refresh를 저장한 직후 무효화되면 거부하고 내 Refresh만 지운다 (그 뒤 다른 로그인이 저장한 Refresh는 남긴다)")
    void login_revokedRightAfterSave() {
        User user = savedUser();
        tokenStore.afterNextSaveRefresh(() -> {
            tokenService.revokeAll(user.getId());
            tokenStore.saveRefresh(user.getId(), "other-session-jti", Duration.ofDays(7));
        });

        assertFails(this::login, UserErrorCode.INVALID_CREDENTIALS);
        assertThat(tokenStore.findRefreshJti(user.getId())).contains("other-session-jti");
    }

    @Test
    @DisplayName("로그인: 이전 무효화가 남아 있어도(기준값 = 그 값) 끼어든 무효화가 없으면 발급한다")
    void login_afterEarlierRevoke() {
        User user = savedUser();
        tokenService.revokeAll(user.getId());

        LoginResult result = login();

        assertThat(tokenStore.findRefreshJti(user.getId())).contains(tokenIssuer.lastRefresh().jti());
        assertThat(result.accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("재발급: 저장된 jti를 확인한 직후 무효화되면 REFRESH_TOKEN_INVALID, 새 Refresh를 남기지 않는다 (기준값은 jti 확인 전)")
    void reissue_revokedRightAfterJtiCheck() {
        User user = savedUser();
        LoginResult login = login();
        tokenStore.afterNextFindRefreshJti(() -> tokenService.revokeAll(user.getId()));

        assertFails(() -> facade.reissue(login.refreshToken()), AuthErrorCode.REFRESH_TOKEN_INVALID);
        assertThat(tokenStore.refreshOf(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("재발급: DB에서 최신 역할을 읽은 직후 무효화되면 REFRESH_TOKEN_INVALID")
    void reissue_revokedRightAfterUserRead() {
        User user = savedUser();
        LoginResult login = login();
        userRepository.afterNextFindActiveById(() -> tokenService.revokeAll(user.getId()));

        assertFails(() -> facade.reissue(login.refreshToken()), AuthErrorCode.REFRESH_TOKEN_INVALID);
        assertThat(tokenStore.refreshOf(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("재발급: 새 Refresh를 저장한 직후 무효화되면 거부하고 내 Refresh만 지운다")
    void reissue_revokedRightAfterSave() {
        User user = savedUser();
        LoginResult login = login();
        tokenStore.afterNextSaveRefresh(() -> {
            tokenService.revokeAll(user.getId());
            tokenStore.saveRefresh(user.getId(), "other-session-jti", Duration.ofDays(7));
        });

        assertFails(() -> facade.reissue(login.refreshToken()), AuthErrorCode.REFRESH_TOKEN_INVALID);
        assertThat(tokenStore.findRefreshJti(user.getId())).contains("other-session-jti");
    }
}
