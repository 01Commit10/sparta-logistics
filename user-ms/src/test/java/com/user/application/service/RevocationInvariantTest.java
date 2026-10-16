package com.user.application.service;

import com.user.application.dto.AuthenticatedUser;
import com.user.application.dto.TokenPairResult;
import com.user.fake.FakeRefreshTokenParser;
import com.user.fake.FakeTokenStore;
import com.user.infrastructure.jwt.JjwtTokenIssuer;
import com.user.infrastructure.jwt.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gateway 규칙(iat <= revoked-at이면 거부)으로 본 불변 조건을 실제 발급기와 같은 초 안의 연속 동작으로 확인한다 (U10 D1).
 * 무효화 전에 받은 토큰은 모두 거부되고, 무효화 뒤에 받은 토큰은 모두 통과해야 한다.
 */
class RevocationInvariantTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci11c2VyLW1zLXRva2VuLWlzc3VlciE=";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    /** 모든 동작이 같은 초 안에서 일어난다 */
    private static final Clock SAME_SECOND = Clock.fixed(Instant.parse("2026-10-16T10:00:00.100Z"), ZoneOffset.UTC);

    private final FakeTokenStore tokenStore = new FakeTokenStore();
    private final TokenService service = new TokenService(
            new JjwtTokenIssuer(new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(7)), SAME_SECOND),
            new FakeRefreshTokenParser(), tokenStore, SAME_SECOND);

    private static long iat(TokenPairResult tokens) {
        return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .clock(() -> java.util.Date.from(Instant.parse("2026-10-16T10:00:10Z"))).build()
                .parseSignedClaims(tokens.accessToken()).getPayload().getIssuedAt().toInstant().getEpochSecond();
    }

    private boolean gatewayRejects(TokenPairResult tokens) {
        return tokenStore.findRevokedAt(USER_ID).map(at -> iat(tokens) <= at.getEpochSecond()).orElse(false);
    }

    @Test
    @DisplayName("같은 초에 로그인 → 무효화 → 재로그인 → 무효화 → 재로그인: 무효화 전 토큰은 모두 거부, 마지막 토큰만 통과")
    void revokeLoginRepeatedWithinOneSecond() {
        AuthenticatedUser user = new AuthenticatedUser(USER_ID, "USER", false);

        TokenPairResult first = service.issue(user, service.revocationSnapshot(USER_ID)).orElseThrow();
        service.revokeAll(USER_ID);
        TokenPairResult second = service.issue(user, service.revocationSnapshot(USER_ID)).orElseThrow();
        assertThat(gatewayRejects(first)).isTrue();
        assertThat(gatewayRejects(second)).isFalse();

        service.revokeAll(USER_ID);
        TokenPairResult third = service.issue(user, service.revocationSnapshot(USER_ID)).orElseThrow();

        assertThat(gatewayRejects(first)).isTrue();
        assertThat(gatewayRejects(second)).as("무효화 전에 받은(미래로 미룬) 토큰도 거부").isTrue();
        assertThat(gatewayRejects(third)).isFalse();
    }
}
