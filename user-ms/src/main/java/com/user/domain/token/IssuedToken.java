package com.user.domain.token;

import java.time.Duration;
import java.time.Instant;

/**
 * 발급한 토큰 문자열과 jti·발급·만료 시각. 토큰 원문이 로그에 남지 않게 toString에서 가린다.
 */
public record IssuedToken(String value, String jti, Instant issuedAt, Instant expiresAt) {

    /** 발급부터 만료까지. Redis 키의 TTL로 쓴다 */
    public Duration lifetime() {
        return Duration.between(issuedAt, expiresAt);
    }

    @Override
    public String toString() {
        return "IssuedToken[value=****, jti=" + jti + ", issuedAt=" + issuedAt + ", expiresAt=" + expiresAt + "]";
    }
}
