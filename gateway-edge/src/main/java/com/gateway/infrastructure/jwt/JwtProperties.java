package com.gateway.infrastructure.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Base64;

/**
 * Access Token 서명 키 (application.yaml jwt.secret-key ← .env JWT_SECRET_KEY).
 * 키가 없거나 HS256에 짧으면(32바이트 미만) 기동을 멈춘다. 값은 로그에 남기지 않는다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secretKey) {

    private static final int MIN_KEY_BYTES = 32;

    public JwtProperties {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("jwt.secret-key(JWT_SECRET_KEY)가 설정되지 않았습니다.");
        }
        if (decode(secretKey).length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException("jwt.secret-key는 Base64로 디코딩해 " + MIN_KEY_BYTES + "바이트 이상이어야 합니다.");
        }
    }

    public byte[] secretKeyBytes() {
        return decode(secretKey);
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
        return "JwtProperties[secretKey=****]";
    }
}
