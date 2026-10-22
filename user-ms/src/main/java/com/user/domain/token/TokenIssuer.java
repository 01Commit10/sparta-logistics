package com.user.domain.token;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 토큰 발급 Port. 구현은 infrastructure.jwt (jjwt, HS256).
 */
public interface TokenIssuer {

    /**
     * revokedAt: 이 사용자의 토큰을 무효로 한 시각 (없으면 {@link Instant#EPOCH}).
     * Gateway는 iat가 revoked-at(초) 이하인 Access를 거부하므로, 발급 시각을 revoked-at 다음 초 이후로 정한다 (U10 D1).
     */
    IssuedToken issueAccess(AccessTokenSpec spec, Instant revokedAt);

    default IssuedToken issueAccess(AccessTokenSpec spec) {
        return issueAccess(spec, Instant.EPOCH);
    }

    /** Refresh Token에는 role·pcr을 넣지 않는다 (재발급 때 DB에서 다시 읽음) */
    IssuedToken issueRefresh(UUID userId);

    /** Access 유효 시간. revoked-at은 이 시간이 지나면 지워도 된다 (그 전에 발급된 Access는 모두 만료) */
    Duration accessTokenLifetime();
}
