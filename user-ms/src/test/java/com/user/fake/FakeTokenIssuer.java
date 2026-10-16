package com.user.fake;

import com.user.domain.token.AccessTokenSpec;
import com.user.domain.token.IssuedToken;
import com.user.domain.token.TokenIssuer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 테스트용: 서명 없이 순번이 붙은 토큰을 만든다. 발급 요청을 기록한다. */
public class FakeTokenIssuer implements TokenIssuer {

    public static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
    public static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    public static final Duration REFRESH_TTL = Duration.ofDays(7);

    private final List<AccessTokenSpec> accessSpecs = new ArrayList<>();
    private final List<UUID> refreshUserIds = new ArrayList<>();
    private final List<IssuedToken> refreshTokens = new ArrayList<>();
    private int sequence;

    @Override
    public IssuedToken issueAccess(AccessTokenSpec spec) {
        accessSpecs.add(spec);
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

    public List<AccessTokenSpec> accessSpecs() {
        return accessSpecs;
    }

    public List<UUID> refreshUserIds() {
        return refreshUserIds;
    }

    public IssuedToken lastRefresh() {
        return refreshTokens.get(refreshTokens.size() - 1);
    }

    public int issuedCount() {
        return sequence;
    }
}
