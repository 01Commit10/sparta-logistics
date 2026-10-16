package com.gateway.support;

import com.common.auth.AuthContract;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * 인증 계약(docs/plan/02 인증 계약 2.1)대로 테스트 토큰을 만든다.
 * user-ms의 발급 코드를 가져오지 않고 jjwt로 직접 만든다 (서비스 독립).
 * claim 이름은 common {@link AuthContract} 상수를 쓴다.
 */
public final class TestTokenFactory {

    /** application-test.yaml의 jwt.secret-key와 같은 값 (테스트 전용) */
    public static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1nYXRld2F5LWVkZ2UtMzJieXRlcyE=";
    /** 서명 위조 테스트용 다른 키 */
    public static final String OTHER_SECRET = "b3RoZXItc2VjcmV0LWtleS1mb3ItZm9yZ2VkLXRva2Vucy0zMmJ5dGVzIQ==";

    public static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    public static final Duration REFRESH_TTL = Duration.ofDays(7);

    public static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    public static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private TestTokenFactory() {
    }

    /** 지금 발급한 유효한 Access Token의 Authorization 헤더 값 (role: MASTER 또는 USER) */
    public static String bearer(String role) {
        UUID userId = "MASTER".equals(role) ? MASTER_ID : USER_ID;
        return "Bearer " + access(userId, role, false, Instant.now());
    }

    public static String access(UUID userId, String role, boolean passwordChangeRequired, Instant issuedAt) {
        return accessSignedWith(SECRET, userId, role, passwordChangeRequired, issuedAt);
    }

    public static String accessSignedWith(String secret, UUID userId, String role,
                                          boolean passwordChangeRequired, Instant issuedAt) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim(AuthContract.CLAIM_ROLE, role)
                .claim(AuthContract.CLAIM_PASSWORD_CHANGE_REQUIRED, passwordChangeRequired)
                .claim(AuthContract.CLAIM_TOKEN_TYPE, AuthContract.TOKEN_TYPE_ACCESS)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(ACCESS_TTL)))
                .signWith(key(secret))
                .compact();
    }

    public static String refresh(UUID userId, Instant issuedAt) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim(AuthContract.CLAIM_TOKEN_TYPE, AuthContract.TOKEN_TYPE_REFRESH)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(REFRESH_TTL)))
                .signWith(key(SECRET))
                .compact();
    }

    public static SecretKey key(String base64Secret) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
    }
}
