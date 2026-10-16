package com.user.application.service;

import com.common.exception.AuthErrorCode;
import com.common.exception.BusinessException;
import com.user.domain.token.RefreshTokenClaims;
import com.user.fake.FakeRefreshTokenParser;
import com.user.fake.FakeTokenIssuer;
import com.user.fake.FakeTokenStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * USR-03 재발급: Refresh Token 검증 (U07 테스트 1~3).
 */
class VerifyRefreshTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String TOKEN = "refresh-token";

    private final FakeRefreshTokenParser parser = new FakeRefreshTokenParser().register(TOKEN, USER_ID, "jti-1");
    private final FakeTokenStore tokenStore = new FakeTokenStore();
    private final TokenService service = new TokenService(new FakeTokenIssuer(), parser, tokenStore, Clock.systemUTC());

    private static void assertRefreshInvalid(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID));
    }

    @Test
    @DisplayName("1. 저장된 jti와 같으면 토큰의 사용자 ID·jti를 돌려주고 저장값은 그대로 둔다")
    void matches() {
        tokenStore.saveRefresh(USER_ID, "jti-1", Duration.ofDays(7));

        RefreshTokenClaims claims = service.verifyRefresh(TOKEN).claims();

        assertThat(claims).isEqualTo(new RefreshTokenClaims(USER_ID, "jti-1"));
        assertThat(tokenStore.findRefreshJti(USER_ID)).contains("jti-1");
        assertThat(tokenStore.deleteCount()).isZero();
    }

    @Test
    @DisplayName("2. 저장값이 없으면(로그아웃·만료) REFRESH_TOKEN_INVALID")
    void notStored() {
        assertRefreshInvalid(() -> service.verifyRefresh(TOKEN));
    }

    @Test
    @DisplayName("3. 저장된 jti와 다르면(이미 교체된 토큰 재사용) REFRESH_TOKEN_INVALID, 저장값을 지워 새 토큰도 못 쓰게 한다")
    void reuseDetected() {
        tokenStore.saveRefresh(USER_ID, "jti-2", Duration.ofDays(7));

        assertRefreshInvalid(() -> service.verifyRefresh(TOKEN));
        assertThat(tokenStore.findRefreshJti(USER_ID)).isEmpty();
        assertThat(tokenStore.deleteCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("파싱에 실패한 토큰(서명·만료·typ)은 저장소를 보지도 지우지도 않는다 (아무 토큰으로 남의 세션을 지울 수 없음)")
    void parseFailureTouchesNothing() {
        tokenStore.saveRefresh(USER_ID, "jti-1", Duration.ofDays(7));

        assertRefreshInvalid(() -> service.verifyRefresh("forged"));
        assertThat(tokenStore.findRefreshJti(USER_ID)).contains("jti-1");
        assertThat(tokenStore.deleteCount()).isZero();
    }
}
