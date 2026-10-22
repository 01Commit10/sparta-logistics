package com.user.fake;

import com.user.domain.token.AccessTokenSpec;
import com.user.domain.token.IssuedToken;
import com.user.domain.token.RefreshTokenClaims;
import com.user.domain.token.TokenIssuer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 테스트용: 서명 없이 순번이 붙은 토큰을 만든다. 발급 요청을 기록한다. */
public class FakeTokenIssuer implements TokenIssuer {

    public static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
    public static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    public static final Duration REFRESH_TTL = Duration.ofDays(7);

    private final List<AccessTokenSpec> accessSpecs = new ArrayList<>();
    private final List<Instant> accessRevokedAts = new ArrayList<>();
    private final List<UUID> refreshUserIds = new ArrayList<>();
    private final List<IssuedToken> refreshTokens = new ArrayList<>();
    private int sequence;

    @Override
    public IssuedToken issueAccess(AccessTokenSpec spec, Instant revokedAt) {
        accessSpecs.add(spec);
        accessRevokedAts.add(revokedAt);
        int n = ++sequence;
        return new IssuedToken("access-" + n, "jti-" + n, NOW, NOW.plus(ACCESS_TTL));
    }

    @Override
    public IssuedToken issueRefresh(UUID userId) {
        refreshUserIds.add(userId);
        int n = ++sequence;
        IssuedToken token = new IssuedToken("refresh-" + n, "jti-" + n, NOW, NOW.plus(REFRESH_TTL));
        refreshTokens.add(token);
        return token;
    }

    @Override
    public Duration accessTokenLifetime() {
        return ACCESS_TTL;
    }

    /** issueAccess에 넘어온 revokedAt (발급 순서대로) */
    public List<Instant> accessRevokedAts() {
        return accessRevokedAts;
    }

    public List<AccessTokenSpec> accessSpecs() {
        return accessSpecs;
    }

    public List<UUID> refreshUserIds() {
        return refreshUserIds;
    }

    /** FakeRefreshTokenParser가 이 발급기가 만든 Refresh를 통과시킬 때 쓴다 */
    public Optional<RefreshTokenClaims> refreshClaimsOf(String value) {
        for (int i = 0; i < refreshTokens.size(); i++) {
            if (refreshTokens.get(i).value().equals(value)) {
                return Optional.of(new RefreshTokenClaims(refreshUserIds.get(i), refreshTokens.get(i).jti()));
            }
        }
        return Optional.empty();
    }

    public IssuedToken lastRefresh() {
        return refreshTokens.get(refreshTokens.size() - 1);
    }

    public int issuedCount() {
        return sequence;
    }
}
