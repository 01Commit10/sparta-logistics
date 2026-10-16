package com.user.infrastructure.jwt;

import com.common.auth.AuthContract;
import com.user.domain.token.AccessTokenSpec;
import com.user.domain.token.IssuedToken;
import com.user.domain.token.TokenIssuer;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * jjwt(HS256)로 Access·Refresh Token을 발급한다 (docs/plan/02 인증 계약 2.1).
 * JWT의 iat·exp는 초 단위라 발급 시각을 초로 잘라, IssuedToken의 시각과 토큰 안의 값이 같게 한다.
 * 비밀번호 변경 등으로 revoked-at이 걸린 같은 초에 다시 로그인하면 iat를 다음 초로 미룬다 (1초 미만 미래 iat).
 * 그렇지 않으면 새 토큰도 iat <= revoked-at이라 revoked-at TTL(30분) 동안 Gateway에서 거부된다 (U10 D1).
 */
@Component
public class JjwtTokenIssuer implements TokenIssuer {

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Clock clock;

    public JjwtTokenIssuer(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.secretKeyBytes());
        this.accessTtl = properties.accessTtl();
        this.refreshTtl = properties.refreshTtl();
        this.clock = clock;
    }

    @Override
    public Duration accessTokenLifetime() {
        return accessTtl;
    }

    @Override
    public IssuedToken issueAccess(AccessTokenSpec spec, Instant revokedAt) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant firstValidSecond = revokedAt.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
        Instant issuedAt = now.isBefore(firstValidSecond) ? firstValidSecond : now;
        return issue(spec.userId(), AuthContract.TOKEN_TYPE_ACCESS, issuedAt, accessTtl, builder -> builder
                .claim(AuthContract.CLAIM_ROLE, spec.role())
                .claim(AuthContract.CLAIM_PASSWORD_CHANGE_REQUIRED, spec.passwordChangeRequired()));
    }

    @Override
    public IssuedToken issueRefresh(UUID userId) {
        return issue(userId, AuthContract.TOKEN_TYPE_REFRESH, clock.instant().truncatedTo(ChronoUnit.SECONDS),
                refreshTtl, builder -> builder);
    }

    private IssuedToken issue(UUID userId, String type, Instant issuedAt, Duration ttl,
                              UnaryOperator<JwtBuilder> extraClaims) {
        Instant expiresAt = issuedAt.plus(ttl);
        String jti = UUID.randomUUID().toString();
        String value = extraClaims.apply(Jwts.builder()
                        .subject(userId.toString())
                        .claim(AuthContract.CLAIM_TOKEN_TYPE, type)
                        .id(jti)
                        .issuedAt(Date.from(issuedAt))
                        .expiration(Date.from(expiresAt)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(value, jti, issuedAt, expiresAt);
    }
}
