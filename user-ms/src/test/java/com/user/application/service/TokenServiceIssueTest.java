package com.user.application.service;

import com.user.application.dto.AuthenticatedUser;
import com.user.application.dto.TokenPairResult;
import com.user.domain.token.AccessTokenSpec;
import com.user.fake.FakeTokenIssuer;
import com.user.fake.FakeTokenStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그인 토큰 발급과 Refresh jti 저장 (U05 테스트 3~4).
 */
class TokenServiceIssueTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final FakeTokenIssuer tokenIssuer = new FakeTokenIssuer();
    private final FakeTokenStore tokenStore = new FakeTokenStore();
    private final TokenService service = new TokenService(tokenIssuer, tokenStore);

    @Test
    @DisplayName("Access에는 사용자 ID·역할·변경 필요 여부를 담고, Refresh는 사용자 ID로 발급한다")
    void issuesAccessAndRefresh() {
        TokenPairResult result = service.issue(new AuthenticatedUser(USER_ID, "MASTER", true));

        assertThat(tokenIssuer.accessSpecs()).containsExactly(new AccessTokenSpec(USER_ID, "MASTER", true));
        assertThat(tokenIssuer.refreshUserIds()).containsExactly(USER_ID);
        assertThat(result.accessToken()).startsWith("access-");
        assertThat(result.refreshToken()).isEqualTo(tokenIssuer.lastRefresh().value());
    }

    @Test
    @DisplayName("Refresh의 jti를 Refresh 유효 시간(7일) 동안 저장한다")
    void savesRefreshJti() {
        service.issue(new AuthenticatedUser(USER_ID, "USER", false));

        FakeTokenStore.Saved saved = tokenStore.refreshOf(USER_ID).orElseThrow();
        assertThat(saved.jti()).isEqualTo(tokenIssuer.lastRefresh().jti());
        assertThat(saved.ttl()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("다시 로그인하면 저장된 jti가 두 번째 Refresh의 것으로 바뀐다 (세션 1개)")
    void secondLoginReplacesRefresh() {
        service.issue(new AuthenticatedUser(USER_ID, "USER", false));
        String firstJti = tokenIssuer.lastRefresh().jti();

        service.issue(new AuthenticatedUser(USER_ID, "USER", false));

        String secondJti = tokenIssuer.lastRefresh().jti();
        assertThat(secondJti).isNotEqualTo(firstJti);
        assertThat(tokenStore.refreshOf(USER_ID).orElseThrow().jti()).isEqualTo(secondJti);
    }
}
