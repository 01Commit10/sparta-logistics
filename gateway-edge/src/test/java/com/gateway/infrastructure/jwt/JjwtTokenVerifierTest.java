package com.gateway.infrastructure.jwt;

import com.common.exception.AuthErrorCode;
import com.gateway.domain.token.AccessTokenClaims;
import com.gateway.domain.token.Role;
import com.gateway.domain.token.VerificationResult;
import com.gateway.support.TestTokenFactory;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JjwtTokenVerifierTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant ISSUED_AT = Instant.parse("2026-10-15T00:00:00Z");

    private static JjwtTokenVerifier verifierAt(Instant now) {
        return new JjwtTokenVerifier(new JwtProperties(TestTokenFactory.SECRET), Clock.fixed(now, ZoneOffset.UTC));
    }

    private final JjwtTokenVerifier verifier = verifierAt(ISSUED_AT.plusSeconds(60));

    @Test
    @DisplayName("계약대로 만든 Access Token의 claim 6개를 읽는다")
    void readsClaims() {
        String token = TestTokenFactory.access(USER_ID, "MASTER", true, ISSUED_AT);

        VerificationResult result = verifier.verify(token);

        assertThat(result.isSuccess()).isTrue();
        AccessTokenClaims claims = result.claims().orElseThrow();
        assertThat(claims.userId()).isEqualTo(USER_ID);
        assertThat(claims.role()).isEqualTo(Role.MASTER);
        assertThat(claims.passwordChangeRequired()).isTrue();
        assertThat(claims.jti()).isNotBlank();
        assertThat(claims.issuedAt()).isEqualTo(ISSUED_AT);
        assertThat(claims.expiresAt()).isEqualTo(ISSUED_AT.plus(TestTokenFactory.ACCESS_TTL));
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 TOKEN_INVALID")
    void otherKey() {
        String token = TestTokenFactory.accessSignedWith(TestTokenFactory.OTHER_SECRET, USER_ID, "USER", false, ISSUED_AT);

        assertFailure(verifier.verify(token), AuthErrorCode.TOKEN_INVALID);
    }

    @Nested
    @DisplayName("만료 (Access 30분)")
    class Expiry {

        private final String token = TestTokenFactory.access(USER_ID, "USER", false, ISSUED_AT);

        @Test
        @DisplayName("발급 29분 뒤에는 통과한다")
        void beforeExpiry() {
            assertThat(verifierAt(ISSUED_AT.plus(Duration.ofMinutes(29))).verify(token).isSuccess()).isTrue();
        }

        @Test
        @DisplayName("발급 31분 뒤에는 TOKEN_EXPIRED")
        void afterExpiry() {
            assertFailure(verifierAt(ISSUED_AT.plus(Duration.ofMinutes(31))).verify(token), AuthErrorCode.TOKEN_EXPIRED);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "a.b", "a.b.c.d", "!!!.@@@.###", "", " "})
    @DisplayName("JWT 형식이 아니면 TOKEN_INVALID")
    void malformed(String token) {
        assertFailure(verifier.verify(token), AuthErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("서명 없는 토큰(alg: none)은 TOKEN_INVALID")
    void unsigned() {
        String header = base64Url("{\"alg\":\"none\"}");
        String payload = base64Url("{\"sub\":\"" + USER_ID + "\",\"role\":\"MASTER\",\"pcr\":false,\"typ\":\"access\","
                + "\"jti\":\"j\",\"iat\":" + ISSUED_AT.getEpochSecond() + ",\"exp\":" + ISSUED_AT.plusSeconds(1800).getEpochSecond() + "}");

        assertFailure(verifier.verify(header + "." + payload + "."), AuthErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("Refresh Token(typ=refresh)을 Access로 쓰면 TOKEN_INVALID")
    void refreshToken() {
        assertFailure(verifier.verify(TestTokenFactory.refresh(USER_ID, ISSUED_AT)), AuthErrorCode.TOKEN_INVALID);
    }

    @Nested
    @DisplayName("claim이 없거나 값이 계약과 다르면 TOKEN_INVALID")
    class InvalidClaims {

        @Test
        void roleMissing() {
            assertFailure(verifier.verify(validBuilder().claim("role", null).compact()), AuthErrorCode.TOKEN_INVALID);
        }

        @Test
        void unknownRole() {
            assertFailure(verifier.verify(validBuilder().claim("role", "ADMIN").compact()), AuthErrorCode.TOKEN_INVALID);
        }

        @Test
        void subjectNotUuid() {
            assertFailure(verifier.verify(validBuilder().subject("master01").compact()), AuthErrorCode.TOKEN_INVALID);
        }

        @Test
        void subjectMissing() {
            assertFailure(verifier.verify(validBuilder().subject(null).compact()), AuthErrorCode.TOKEN_INVALID);
        }

        @Test
        @DisplayName("role·pcr가 모두 있어도 typ이 access가 아니면 거부한다")
        void typNotAccess() {
            assertFailure(verifier.verify(validBuilder().claim("typ", "refresh").compact()), AuthErrorCode.TOKEN_INVALID);
        }

        @Test
        void typMissing() {
            assertFailure(verifier.verify(validBuilder().claim("typ", null).compact()), AuthErrorCode.TOKEN_INVALID);
        }

        @Test
        void pcrMissing() {
            assertFailure(verifier.verify(validBuilder().claim("pcr", null).compact()), AuthErrorCode.TOKEN_INVALID);
        }

        @Test
        void pcrNotBoolean() {
            assertFailure(verifier.verify(validBuilder().claim("pcr", "yes").compact()), AuthErrorCode.TOKEN_INVALID);
        }

        @Test
        void jtiMissing() {
            assertFailure(verifier.verify(validBuilder().id(null).compact()), AuthErrorCode.TOKEN_INVALID);
        }

        @Test
        @DisplayName("exp가 없으면 영원히 유효한 토큰이 되므로 거부한다")
        void expMissing() {
            assertFailure(verifier.verify(validBuilder().expiration(null).compact()), AuthErrorCode.TOKEN_INVALID);
        }

        @Test
        void iatMissing() {
            assertFailure(verifier.verify(validBuilder().issuedAt(null).compact()), AuthErrorCode.TOKEN_INVALID);
        }

        /** 계약을 모두 지킨 Access Token 빌더. 테스트마다 한 가지만 바꾼다 (null 값은 claim 제거) */
        private JwtBuilder validBuilder() {
            return Jwts.builder()
                    .subject(USER_ID.toString())
                    .claim("role", "USER")
                    .claim("pcr", false)
                    .claim("typ", "access")
                    .id(UUID.randomUUID().toString())
                    .issuedAt(Date.from(ISSUED_AT))
                    .expiration(Date.from(ISSUED_AT.plus(TestTokenFactory.ACCESS_TTL)))
                    .signWith(TestTokenFactory.key(TestTokenFactory.SECRET));
        }
    }

    @Test
    @DisplayName("빌더 기본값은 유효하다 (위 claim 테스트가 한 가지 이유로만 실패한다는 전제)")
    void validBuilderIsValid() {
        String token = new InvalidClaims().validBuilder().compact();

        assertThat(verifier.verify(token).isSuccess()).isTrue();
    }

    private static void assertFailure(VerificationResult result, AuthErrorCode expected) {
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).contains(expected);
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
