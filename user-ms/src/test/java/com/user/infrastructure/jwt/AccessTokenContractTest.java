package com.user.infrastructure.jwt;

import com.user.domain.token.AccessTokenSpec;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약 테스트 (U05 테스트 8, docs/plan/02 인증 계약 2.1·4장): gateway-edge가 읽는 Access Token 형식.
 * 계약이 바뀌면 이 테스트가 깨지도록 claim 이름을 AuthContract 상수가 아니라 계약 문서의 문자열로 쓴다.
 * gateway-edge의 AccessTokenContractTest(GW-12)는 같은 표로 만든 토큰을 읽는지 본다.
 */
class AccessTokenContractTest {

    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final JjwtTokenIssuer issuer = new JjwtTokenIssuer(
            new JwtProperties(JwtPropertiesTest.SECRET, Duration.ofMinutes(30), Duration.ofDays(7)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(JwtPropertiesTest.SECRET)))
                .clock(() -> Date.from(NOW))
                .build()
                .parseSignedClaims(token);
    }

    @Test
    @DisplayName("Access Token: HS256, claim은 정확히 sub·role·pcr·typ·jti·iat·exp, 타입과 값이 계약표와 같다")
    void accessToken() {
        Jws<Claims> jws = parse(issuer.issueAccess(new AccessTokenSpec(USER_ID, "USER", false)).value());
        Claims claims = jws.getPayload();

        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(claims.keySet()).isEqualTo(Set.of("sub", "role", "pcr", "typ", "jti", "iat", "exp"));
        assertThat(claims.get("sub")).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(claims.get("role")).isEqualTo("USER");
        assertThat(claims.get("pcr")).isEqualTo(Boolean.FALSE);
        assertThat(claims.get("typ")).isEqualTo("access");
        assertThat(UUID.fromString((String) claims.get("jti"))).isNotNull();
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(1800));
    }

    @Test
    @DisplayName("키가 64바이트 이상이어도 HS256으로 서명한다 (jjwt는 키 길이로 HS512를 고르므로 알고리즘을 고정)")
    void hs256EvenWithLongKey() {
        String longKey = Base64.getEncoder().encodeToString("k".repeat(64).getBytes());
        JjwtTokenIssuer longKeyIssuer = new JjwtTokenIssuer(
                new JwtProperties(longKey, Duration.ofMinutes(30), Duration.ofDays(7)), Clock.fixed(NOW, ZoneOffset.UTC));

        String token = longKeyIssuer.issueAccess(new AccessTokenSpec(USER_ID, "USER", false)).value();

        String header = new String(Base64.getUrlDecoder().decode(token.substring(0, token.indexOf('.'))));
        assertThat(header).contains("\"alg\":\"HS256\"");
    }

    @Test
    @DisplayName("Refresh Token: HS256, claim은 정확히 sub·typ·jti·iat·exp, exp = iat + 7일")
    void refreshToken() {
        Jws<Claims> jws = parse(issuer.issueRefresh(USER_ID).value());
        Claims claims = jws.getPayload();

        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(claims.keySet()).isEqualTo(Set.of("sub", "typ", "jti", "iat", "exp"));
        assertThat(claims.get("typ")).isEqualTo("refresh");
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(7 * 24 * 3600));
    }
}
