package com.user.infrastructure.jwt;

import com.user.domain.token.AccessTokenSpec;
import com.user.domain.token.IssuedToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 발급한 토큰의 claim 값 (U05 테스트 6~7). claim 이름 자체의 계약은 AccessTokenContractTest가 본다.
 */
class JjwtTokenIssuerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    /** 밀리초가 있는 시각: 토큰 iat는 초 단위라, IssuedToken의 시각도 초로 잘라야 토큰과 같다 */
    private static final Instant NOW = Instant.parse("2026-10-16T01:02:03.456Z");
    private static final Instant NOW_SECONDS = Instant.parse("2026-10-16T01:02:03Z");

    private final JjwtTokenIssuer issuer = new JjwtTokenIssuer(
            new JwtProperties(JwtPropertiesTest.SECRET, Duration.ofMinutes(30), Duration.ofDays(7)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(new JwtProperties(JwtPropertiesTest.SECRET,
                        Duration.ofMinutes(30), Duration.ofDays(7)).secretKeyBytes()))
                .clock(() -> Date.from(NOW))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Test
    @DisplayName("Access: sub·role·pcr·typ=access·jti·iat, exp = iat + 30분")
    void access() {
        IssuedToken token = issuer.issueAccess(new AccessTokenSpec(USER_ID, "MASTER", true));

        Claims claims = parse(token.value());
        assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("MASTER");
        assertThat(claims.get("pcr", Boolean.class)).isTrue();
        assertThat(claims.get("typ", String.class)).isEqualTo("access");
        assertThat(claims.getId()).isEqualTo(token.jti());
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW_SECONDS);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW_SECONDS.plus(Duration.ofMinutes(30)));
        assertThat(token.issuedAt()).isEqualTo(NOW_SECONDS);
        assertThat(token.expiresAt()).isEqualTo(NOW_SECONDS.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("Refresh: sub·typ=refresh·jti, role·pcr 없음, exp = iat + 7일")
    void refresh() {
        IssuedToken token = issuer.issueRefresh(USER_ID);

        Claims claims = parse(token.value());
        assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(claims.get("typ", String.class)).isEqualTo("refresh");
        assertThat(claims.getId()).isEqualTo(token.jti());
        assertThat(claims).doesNotContainKeys("role", "pcr");
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW_SECONDS);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW_SECONDS.plus(Duration.ofDays(7)));
        assertThat(token.lifetime()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("U10 D1: revoked-at과 같은 초면 iat를 다음 초로 미룬다 (Gateway는 iat <= revoked-at을 거부)")
    void sameSecondAsRevokedAt() {
        IssuedToken token = issuer.issueAccess(new AccessTokenSpec(USER_ID, "USER", false), NOW_SECONDS);

        Claims claims = parse(token.value());
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW_SECONDS.plusSeconds(1));
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW_SECONDS.plusSeconds(1).plus(Duration.ofMinutes(30)));
        assertThat(token.issuedAt()).isEqualTo(NOW_SECONDS.plusSeconds(1));
    }

    @Test
    @DisplayName("U10 D1: revoked-at이 이전 초면 지금 시각 그대로, 다른 인스턴스 시계가 앞서 미래면 그 다음 초")
    void revokedAtBeforeOrAfterNow() {
        assertThat(issuer.issueAccess(new AccessTokenSpec(USER_ID, "USER", false), NOW_SECONDS.minusSeconds(1)).issuedAt())
                .isEqualTo(NOW_SECONDS);
        assertThat(issuer.issueAccess(new AccessTokenSpec(USER_ID, "USER", false), Instant.EPOCH).issuedAt())
                .isEqualTo(NOW_SECONDS);
        assertThat(issuer.issueAccess(new AccessTokenSpec(USER_ID, "USER", false), NOW_SECONDS.plusSeconds(2)).issuedAt())
                .isEqualTo(NOW_SECONDS.plusSeconds(3));
        assertThat(issuer.accessTokenLifetime()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("jti는 UUID이고 발급마다 다르다")
    void uniqueJti() {
        IssuedToken first = issuer.issueRefresh(USER_ID);
        IssuedToken second = issuer.issueRefresh(USER_ID);

        assertThat(UUID.fromString(first.jti())).isNotNull();
        assertThat(first.jti()).isNotEqualTo(second.jti());
        assertThat(first.value()).isNotEqualTo(second.value());
    }

    @Test
    @DisplayName("toString에 토큰 원문을 넣지 않는다")
    void toStringMasksValue() {
        IssuedToken token = issuer.issueAccess(new AccessTokenSpec(USER_ID, "USER", false));

        assertThat(token.toString()).doesNotContain(token.value());
    }
}
