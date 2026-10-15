package com.gateway.support;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 테스트 토큰이 인증 계약(docs/plan/02 인증 계약 2.1)과 같은 형식인지 확인한다.
 */
class TestTokenFactoryTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant ISSUED_AT = Instant.parse("2026-10-15T00:00:00Z");

    @Test
    @DisplayName("Access Token에는 sub, role, pcr, typ=access, jti, iat, exp(30분)가 있다")
    void accessToken_hasContractClaims() {
        String token = TestTokenFactory.access(USER_ID, "MASTER", true, ISSUED_AT);

        Claims claims = parse(token);

        assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("MASTER");
        assertThat(claims.get("pcr", Boolean.class)).isTrue();
        assertThat(claims.get("typ", String.class)).isEqualTo("access");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(ISSUED_AT);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(ISSUED_AT.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("Refresh Token에는 role·pcr이 없고 typ=refresh, exp는 7일이다")
    void refreshToken_hasNoRole() {
        String token = TestTokenFactory.refresh(USER_ID, ISSUED_AT);

        Claims claims = parse(token);

        assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(claims.get("typ", String.class)).isEqualTo("refresh");
        assertThat(claims.get("role")).isNull();
        assertThat(claims.get("pcr")).isNull();
        assertThat(claims.getExpiration().toInstant()).isEqualTo(ISSUED_AT.plus(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("토큰마다 jti가 다르다")
    void jti_isUnique() {
        String first = TestTokenFactory.access(USER_ID, "USER", false, ISSUED_AT);
        String second = TestTokenFactory.access(USER_ID, "USER", false, ISSUED_AT);

        assertThat(parse(first).getId()).isNotEqualTo(parse(second).getId());
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 테스트 키로 검증되지 않는다")
    void signedWithOtherKey_failsVerification() {
        String token = TestTokenFactory.accessSignedWith(
                TestTokenFactory.OTHER_SECRET, USER_ID, "USER", false, ISSUED_AT);

        assertThatThrownBy(() -> parse(token)).isInstanceOf(SignatureException.class);
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(TestTokenFactory.key(TestTokenFactory.SECRET))
                .clock(() -> Date.from(ISSUED_AT))   // 만료 검사를 발급 시각 기준으로
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
