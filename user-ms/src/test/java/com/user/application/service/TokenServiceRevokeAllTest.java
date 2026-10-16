package com.user.application.service;

import com.user.application.dto.AuthenticatedUser;
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
 * 사용자의 모든 토큰 무효화 (U10 테스트 4). U11·U15·U17·U18이 같이 쓴다.
 */
class TokenServiceRevokeAllTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00.700Z");

    private final FakeTokenStore tokenStore = new FakeTokenStore();
    private final FakeTokenIssuer tokenIssuer = new FakeTokenIssuer();
    private final TokenService service = new TokenService(tokenIssuer, new FakeRefreshTokenParser(), tokenStore,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("4. revoked-at = 지금(초 단위로 자름), TTL = Access 유효 시간(30분), Refresh 삭제")
    void revokesAll() {
        tokenStore.saveRefresh(USER_ID, "refresh-jti", Duration.ofDays(7));

        service.revokeAll(USER_ID);

        assertThat(tokenStore.revokedOf(USER_ID)).contains(
                new FakeTokenStore.Revoked(Instant.parse("2026-10-16T10:00:00Z"), FakeTokenIssuer.ACCESS_TTL));
        assertThat(tokenStore.refreshOf(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("무효화한 뒤 같은 초에 다시 로그인해도 새 Access의 발급 기준은 revoked-at이다 (발급기가 다음 초로 미룸)")
    void loginAfterRevokeUsesRevokedAt() {
        service.revokeAll(USER_ID);

        service.issue(new AuthenticatedUser(USER_ID, "USER", false));

        assertThat(tokenIssuer.accessRevokedAts()).containsExactly(Instant.parse("2026-10-16T10:00:00Z"));
    }

    @Test
    @DisplayName("이미 revoked-at이 지금 초 이상이면(같은 초에 다시 로그인해 iat가 다음 초로 미뤄진 토큰이 있음) 그 다음 초로 올린다")
    void bumpsPastPreviousRevokedAt() {
        Instant now = Instant.parse("2026-10-16T10:00:00Z");
        tokenStore.revokeAllIssuedBefore(USER_ID, now, FakeTokenIssuer.ACCESS_TTL);

        service.revokeAll(USER_ID);

        assertThat(tokenStore.revokedOf(USER_ID)).contains(
                new FakeTokenStore.Revoked(now.plusSeconds(1), FakeTokenIssuer.ACCESS_TTL.plusSeconds(1)));
    }

    @Test
    @DisplayName("이전 revoked-at이 지난 초면 지금 초로 쓴다")
    void olderRevokedAtIsReplacedByNow() {
        tokenStore.revokeAllIssuedBefore(USER_ID, Instant.parse("2026-10-16T09:59:00Z"), FakeTokenIssuer.ACCESS_TTL);

        service.revokeAll(USER_ID);

        assertThat(tokenStore.revokedOf(USER_ID)).contains(
                new FakeTokenStore.Revoked(Instant.parse("2026-10-16T10:00:00Z"), FakeTokenIssuer.ACCESS_TTL));
    }
}
