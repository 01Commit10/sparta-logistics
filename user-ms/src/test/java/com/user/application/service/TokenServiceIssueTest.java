package com.user.application.service;

import com.user.application.dto.AuthenticatedUser;
import com.user.application.dto.TokenPairResult;
import com.user.domain.token.AccessTokenSpec;
import com.user.fake.FakeRefreshTokenParser;
import com.user.fake.FakeTokenIssuer;
import com.user.fake.FakeTokenStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그인 토큰 발급과 Refresh jti 저장 (U05 테스트 3~4).
 */
class TokenServiceIssueTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final FakeTokenIssuer tokenIssuer = new FakeTokenIssuer();
    private final FakeTokenStore tokenStore = new FakeTokenStore();
    private final TokenService service = new TokenService(tokenIssuer, new FakeRefreshTokenParser(), tokenStore, Clock.systemUTC());

    /** 무효화가 끼어들지 않는 보통의 발급: 발급 직전에 기준값을 읽는다 */
    private TokenPairResult issue(AuthenticatedUser user) {
        return service.issue(user, service.revocationSnapshot(user.id())).orElseThrow();
    }

    @Test
    @DisplayName("Access에는 사용자 ID·역할·변경 필요 여부를 담고, Refresh는 사용자 ID로 발급한다")
    void issuesAccessAndRefresh() {
        TokenPairResult result = issue(new AuthenticatedUser(USER_ID, "MASTER", true));

        assertThat(tokenIssuer.accessSpecs()).containsExactly(new AccessTokenSpec(USER_ID, "MASTER", true));
        assertThat(tokenIssuer.refreshUserIds()).containsExactly(USER_ID);
        assertThat(result.accessToken()).startsWith("access-");
        assertThat(result.refreshToken()).isEqualTo(tokenIssuer.lastRefresh().value());
    }

    @Test
    @DisplayName("U10 D1: 저장된 revoked-at을 발급기에 넘긴다 (없으면 EPOCH)")
    void passesRevokedAt() {
        issue(new AuthenticatedUser(USER_ID, "USER", false));
        Instant revokedAt = Instant.parse("2026-10-16T10:00:00Z");
        tokenStore.revokeAllIssuedBefore(USER_ID, revokedAt, Duration.ofMinutes(30));

        issue(new AuthenticatedUser(USER_ID, "USER", false));

        assertThat(tokenIssuer.accessRevokedAts()).containsExactly(Instant.EPOCH, revokedAt);
    }

    @Test
    @DisplayName("Refresh의 jti를 Refresh 유효 시간(7일) 동안 저장한다")
    void savesRefreshJti() {
        issue(new AuthenticatedUser(USER_ID, "USER", false));

        FakeTokenStore.Saved saved = tokenStore.refreshOf(USER_ID).orElseThrow();
        assertThat(saved.jti()).isEqualTo(tokenIssuer.lastRefresh().jti());
        assertThat(saved.ttl()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("다시 로그인하면 저장된 jti가 두 번째 Refresh의 것으로 바뀐다 (세션 1개)")
    void secondLoginReplacesRefresh() {
        issue(new AuthenticatedUser(USER_ID, "USER", false));
        String firstJti = tokenIssuer.lastRefresh().jti();

        issue(new AuthenticatedUser(USER_ID, "USER", false));

        String secondJti = tokenIssuer.lastRefresh().jti();
        assertThat(secondJti).isNotEqualTo(firstJti);
        assertThat(tokenStore.refreshOf(USER_ID).orElseThrow().jti()).isEqualTo(secondJti);
    }

    @Test
    @DisplayName("리뷰: 기준값 뒤에 무효화가 지나갔으면 발급하지 않는다 (empty, 토큰을 만들지도 저장하지도 않음)")
    void revokedSinceSnapshot() {
        Instant snapshot = service.revocationSnapshot(USER_ID);
        tokenStore.revokeAllIssuedBefore(USER_ID, Instant.parse("2026-10-16T10:00:00Z"), Duration.ofMinutes(30));

        assertThat(service.issue(new AuthenticatedUser(USER_ID, "USER", false), snapshot)).isEmpty();
        assertThat(tokenIssuer.issuedCount()).isZero();
        assertThat(tokenStore.refreshOf(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("리뷰: 저장 뒤 다시 읽은 revoked-at이 기준값보다 크면 empty, 내 jti일 때만 지운다")
    void revokedRightAfterSave() {
        Instant snapshot = service.revocationSnapshot(USER_ID);
        tokenStore.afterNextSaveRefresh(() ->
                tokenStore.revokeAllIssuedBefore(USER_ID, Instant.parse("2026-10-16T10:00:00Z"), Duration.ofMinutes(30)));

        assertThat(service.issue(new AuthenticatedUser(USER_ID, "USER", false), snapshot)).isEmpty();
        assertThat(tokenStore.refreshOf(USER_ID)).isEmpty();
        assertThat(tokenStore.operations()).containsExactly("saveRefresh", "revokeAllIssuedBefore", "deleteRefreshIfMatches");
    }

    @Test
    @DisplayName("리뷰: revoked-at이 TTL로 사라져 기준값보다 작아진 것은 무효화가 아니다 (발급한다)")
    void revokedAtExpired() {
        tokenStore.revokeAllIssuedBefore(USER_ID, Instant.parse("2026-10-16T10:00:00Z"), Duration.ofMinutes(30));
        Instant snapshot = service.revocationSnapshot(USER_ID);
        FakeTokenStore expired = new FakeTokenStore();
        TokenService afterExpiry = new TokenService(tokenIssuer, new FakeRefreshTokenParser(), expired, Clock.systemUTC());

        assertThat(afterExpiry.issue(new AuthenticatedUser(USER_ID, "USER", false), snapshot)).isPresent();
        assertThat(expired.refreshOf(USER_ID)).isPresent();
    }

    @Test
    @DisplayName("기준값: 저장된 revoked-at, 없으면 EPOCH")
    void snapshot() {
        assertThat(service.revocationSnapshot(USER_ID)).isEqualTo(Instant.EPOCH);
        tokenStore.revokeAllIssuedBefore(USER_ID, Instant.parse("2026-10-16T10:00:00Z"), Duration.ofMinutes(30));
        assertThat(service.revocationSnapshot(USER_ID)).isEqualTo(Instant.parse("2026-10-16T10:00:00Z"));
    }
}
