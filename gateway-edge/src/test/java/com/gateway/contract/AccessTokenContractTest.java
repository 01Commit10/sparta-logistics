package com.gateway.contract;

import com.common.exception.AuthErrorCode;
import com.gateway.domain.token.AccessTokenClaims;
import com.gateway.domain.token.Role;
import com.gateway.domain.token.VerificationResult;
import com.gateway.infrastructure.jwt.JjwtTokenVerifier;
import com.gateway.infrastructure.jwt.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약 테스트 (GW-12 테스트 1·2, docs/plan/02 인증 계약 2.1): user-ms가 발급하는 Access Token을 Gateway가 읽는다.
 * 계약이 바뀌면 깨지도록 claim 이름을 AuthContract 상수가 아니라 계약 문서의 문자열로 쓴다.
 * user-ms의 AccessTokenContractTest는 같은 표대로 발급하는지 본다. 실제 발급 토큰의 교차 확인은 E2E에서 한다.
 */
class AccessTokenContractTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1nYXRld2F5LWVkZ2UtMzJieXRlcyE=";
    private static final Instant IAT = Instant.parse("2026-10-16T00:00:00Z");

    private final JjwtTokenVerifier verifier = new JjwtTokenVerifier(
            new JwtProperties(SECRET), Clock.fixed(IAT.plusSeconds(60), ZoneOffset.UTC));

    /** 계약표 2.1: HS256, sub·role·pcr·typ·jti·iat·exp(iat + 30분) */
    private static String contractToken(String typ) {
        return Jwts.builder()
                .header().add("alg", "HS256").and()
                .subject("00000000-0000-0000-0000-000000000001")
                .claim("role", "USER")
                .claim("pcr", true)
                .claim("typ", typ)
                .id("9b2f1c1e-0000-4000-8000-000000000001")
                .issuedAt(Date.from(IAT))
                .expiration(Date.from(IAT.plusSeconds(1800)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    @DisplayName("1. 계약표대로 만든 Access Token → 성공, claim 6개를 계약대로 읽는다")
    void readsContractToken() {
        VerificationResult result = verifier.verify(contractToken("access"));

        AccessTokenClaims claims = result.claims().orElseThrow();
        assertThat(claims.userId().toString()).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(claims.role()).isEqualTo(Role.USER);
        assertThat(claims.passwordChangeRequired()).isTrue();
        assertThat(claims.jti()).isEqualTo("9b2f1c1e-0000-4000-8000-000000000001");
        assertThat(claims.issuedAt()).isEqualTo(IAT);
        assertThat(claims.expiresAt()).isEqualTo(IAT.plusSeconds(1800));
    }

    @Test
    @DisplayName("2. 계약에서 typ만 refresh로 바꾼 토큰 → TOKEN_INVALID (Refresh를 Access로 쓰지 못한다)")
    void refreshTypeRejected() {
        VerificationResult result = verifier.verify(contractToken("refresh"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).contains(AuthErrorCode.TOKEN_INVALID);
    }
}
