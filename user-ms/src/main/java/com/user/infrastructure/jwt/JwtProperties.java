package com.user.infrastructure.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Base64;

/**
 * 토큰 서명 키와 유효 시간 (application.yaml jwt.* ← .env JWT_SECRET_KEY). docs/plan/02 인증 계약 2.1.
 * 키가 없거나 HS256에 짧으면(32바이트 미만) 기동을 멈춘다. gateway-edge와 같은 키를 쓴다. 키 값은 로그에 남기지 않는다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secretKey, Duration accessTtl, Duration refreshTtl) {

    private static final int MIN_KEY_BYTES = 32;

    public JwtProperties {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("jwt.secret-key(JWT_SECRET_KEY)가 설정되지 않았습니다.");
        }
        if (decode(secretKey).length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException("jwt.secret-key는 Base64로 디코딩해 " + MIN_KEY_BYTES + "바이트 이상이어야 합니다.");
        }
        requirePositive(accessTtl, "jwt.access-ttl");
        requirePositive(refreshTtl, "jwt.refresh-ttl");
    }

    public byte[] secretKeyBytes() {
        return decode(secretKey);
    }

    private static void requirePositive(Duration ttl, String name) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(name + "는 0보다 커야 합니다.");
        }
    }

    private static byte[] decode(String key) {
        try {
            return Base64.getDecoder().decode(key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("jwt.secret-key가 Base64 형식이 아닙니다.");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[secretKey=****, accessTtl=" + accessTtl + ", refreshTtl=" + refreshTtl + "]";
    }
}
