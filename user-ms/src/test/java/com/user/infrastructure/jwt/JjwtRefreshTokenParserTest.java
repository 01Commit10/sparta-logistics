package com.user.infrastructure.jwt;

import com.common.exception.AuthErrorCode;
import com.common.exception.BusinessException;
import com.user.domain.token.AccessTokenSpec;
import com.user.domain.token.IssuedToken;
import com.user.domain.token.RefreshTokenClaims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Refresh Token 검증 (U07 테스트 7). 실제 발급기(JjwtTokenIssuer)가 만든 토큰으로 확인한다.
 */
class JjwtRefreshTokenParserTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-16T01:00:00Z");
    private static final JwtProperties PROPERTIES =
            new JwtProperties(JwtPropertiesTest.SECRET, Duration.ofMinutes(30), Duration.ofDays(7));
    private static final String OTHER_SECRET =
            Base64.getEncoder().encodeToString("another-secret-key-that-is-32-bytes!!".getBytes());

    private final JjwtTokenIssuer issuer = new JjwtTokenIssuer(PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));

    private static JjwtRefreshTokenParser parserAt(Instant now) {
        return new JjwtRefreshTokenParser(PROPERTIES, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static void assertRefreshInvalid(String token, Instant now) {
        assertThatThrownBy(() -> parserAt(now).parse(token))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID));
    }

    /** 테스트 키로 서명한 Refresh 모양 토큰. customize로 claim을 바꿔 계약 위반을 만든다 (typ는 null이면 넣지 않음) */
    private static String signed(String typ, UnaryOperator<io.jsonwebtoken.JwtBuilder> customize) {
        return customize.apply(Jwts.builder()
                        .subject(USER_ID.toString())
                        .claim("typ", typ)
                        .id("jti-1")
                        .issuedAt(Date.from(NOW))
                        .expiration(Date.from(NOW.plus(Duration.ofDays(7)))))
                .signWith(Keys.hmacShaKeyFor(PROPERTIES.secretKeyBytes()), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    @DisplayName("발급한 Refresh는 사용자 ID·jti를 돌려준다 (만료 직전까지)")
    void valid() {
        IssuedToken refresh = issuer.issueRefresh(USER_ID);

        assertThat(parserAt(NOW).parse(refresh.value())).isEqualTo(new RefreshTokenClaims(USER_ID, refresh.jti()));
        assertThat(parserAt(refresh.expiresAt().minusSeconds(1)).parse(refresh.value()).jti()).isEqualTo(refresh.jti());
    }

    @Test
    @DisplayName("만료된 Refresh(7일 경과)는 REFRESH_TOKEN_INVALID (만료를 따로 알리지 않음)")
    void expired() {
        IssuedToken refresh = issuer.issueRefresh(USER_ID);

        assertRefreshInvalid(refresh.value(), refresh.expiresAt().plusSeconds(1));
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 REFRESH_TOKEN_INVALID")
    void otherKey() {
        JjwtTokenIssuer otherIssuer = new JjwtTokenIssuer(
                new JwtProperties(OTHER_SECRET, Duration.ofMinutes(30), Duration.ofDays(7)), Clock.fixed(NOW, ZoneOffset.UTC));

        assertRefreshInvalid(otherIssuer.issueRefresh(USER_ID).value(), NOW);
    }

    @Test
    @DisplayName("Access Token(typ=access)으로는 재발급할 수 없다")
    void accessToken() {
        String access = issuer.issueAccess(new AccessTokenSpec(USER_ID, "MASTER", false)).value();

        assertRefreshInvalid(access, NOW);
    }

    @Test
    @DisplayName("typ가 없거나 refresh가 아니거나, sub·jti가 없거나 sub가 UUID가 아니면 REFRESH_TOKEN_INVALID (서명은 맞음)")
    void missingClaims() {
        assertRefreshInvalid(signed(null, b -> b), NOW);
        assertRefreshInvalid(signed("REFRESH", b -> b), NOW);
        assertRefreshInvalid(signed("refresh", b -> b.subject(null)), NOW);
        assertRefreshInvalid(signed("refresh", b -> b.id(null)), NOW);
        assertRefreshInvalid(signed("refresh", b -> b.subject("not-a-uuid")), NOW);
        assertThat(parserAt(NOW).parse(signed("refresh", b -> b)).jti()).isEqualTo("jti-1");
    }

    @Test
    @DisplayName("서명 없는 토큰(alg=none)은 REFRESH_TOKEN_INVALID")
    void unsigned() {
        String unsigned = Jwts.builder().subject(USER_ID.toString()).claim("typ", "refresh").id("jti-1")
                .expiration(Date.from(NOW.plus(Duration.ofDays(7)))).compact();

        assertRefreshInvalid(unsigned, NOW);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "abc", "a.b.c"})
    @DisplayName("JWT 형식이 아니면 REFRESH_TOKEN_INVALID")
    void malformed(String token) {
        assertRefreshInvalid(token, NOW);
    }
}
