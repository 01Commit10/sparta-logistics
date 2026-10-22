package com.user.application.service;

import com.user.application.dto.LogoutCommand;
import com.user.fake.FakeRefreshTokenParser;
import com.user.fake.FakeTokenIssuer;
import com.user.fake.FakeTokenStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * USR-04 로그아웃 (U08 테스트 1~3).
 */
class TokenServiceLogoutTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");

    private final FakeTokenStore tokenStore = new FakeTokenStore();
    private final TokenService service = new TokenService(new FakeTokenIssuer(), new FakeRefreshTokenParser(), tokenStore, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("1. Access Token을 남은 유효 시간(exp - now) 동안 blacklist에 올린다")
    void blacklistsForRemainingLifetime() {
        service.logout(new LogoutCommand(USER_ID, "jti-1", NOW.plus(Duration.ofMinutes(12))));

        assertThat(tokenStore.blacklistTtlOf("jti-1")).contains(Duration.ofMinutes(12));
    }

    @Test
    @DisplayName("2. Refresh Token을 지운다 (재발급 불가)")
    void deletesRefresh() {
        tokenStore.saveRefresh(USER_ID, "refresh-jti", Duration.ofDays(7));

        service.logout(new LogoutCommand(USER_ID, "jti-1", NOW.plus(Duration.ofMinutes(12))));

        assertThat(tokenStore.refreshOf(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("3. 이미 만료된 토큰(exp <= now)은 blacklist에 올리지 않고 Refresh만 지운다")
    void expiredToken() {
        tokenStore.saveRefresh(USER_ID, "refresh-jti", Duration.ofDays(7));

        service.logout(new LogoutCommand(USER_ID, "jti-1", NOW));

        assertThat(tokenStore.blacklistTtlOf("jti-1")).isEmpty();
        assertThat(tokenStore.refreshOf(USER_ID)).isEmpty();
    }
}
