package com.gateway.application.auth;

import com.common.exception.AuthErrorCode;
import com.common.exception.CommonErrorCode;
import com.common.exception.ErrorCode;
import com.gateway.application.auth.dto.AuthRequest;
import com.gateway.application.auth.dto.AuthResult;
import com.gateway.domain.policy.RoutePolicy;
import com.gateway.domain.token.AccessTokenClaims;
import com.gateway.domain.token.Role;
import com.gateway.domain.token.TokenState;
import com.gateway.domain.token.VerificationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class AuthenticationServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant ISSUED_AT = Instant.parse("2026-10-15T00:00:00Z");

    private static final RoutePolicy POLICY = RoutePolicy.builder()
            .publicRules(List.of("POST /api/v1/auth/login"))
            .masterExcludeRules(List.of("* /api/v1/users/me"))
            .masterRules(List.of("GET /api/v1/users", "GET /api/v1/users/*"))
            .passwordChangeAllowedRules(List.of("GET /api/v1/users/me", "PATCH /api/v1/users/me/password",
                    "POST /api/v1/auth/logout"))
            .build();

    private static VerificationResult validToken(Role role) {
        return validToken(role, false);
    }

    private static VerificationResult validToken(Role role, boolean passwordChangeRequired) {
        return VerificationResult.success(new AccessTokenClaims(USER_ID, role, passwordChangeRequired, "jti-1",
                ISSUED_AT, ISSUED_AT.plusSeconds(1800)));
    }

    private static AuthRequest request(String method, String path, String authorization) {
        return new AuthRequest(method, path, authorization);
    }

    @Nested
    @DisplayName("인증 (GW-05)")
    class Authentication {

        @Test
        @DisplayName("공개 경로는 토큰 검증 없이 비로그인으로 통과한다 (토큰이 있어도 헤더를 붙이지 않는다)")
        void publicPath() {
            FakeTokenVerifier verifier = new FakeTokenVerifier(validToken(Role.USER));
            AuthenticationService service = new AuthenticationService(POLICY, verifier, new FakeTokenStateReader());

            StepVerifier.create(service.authenticate(request("POST", "/api/v1/auth/login", "Bearer t")))
                    .assertNext(result -> {
                        assertThat(result.allowed()).isTrue();
                        assertThat(result.headers()).isEmpty();
                    })
                    .verifyComplete();
            assertThat(verifier.received()).isEmpty();
        }

        @Test
        @DisplayName("보호 경로에 토큰이 없으면 TOKEN_MISSING, 검증기를 부르지 않는다")
        void missingToken() {
            FakeTokenVerifier verifier = new FakeTokenVerifier(validToken(Role.USER));
            AuthenticationService service = new AuthenticationService(POLICY, verifier, new FakeTokenStateReader());

            StepVerifier.create(service.authenticate(request("GET", "/api/v1/orders", null)))
                    .assertNext(result -> assertDenied(result, AuthErrorCode.TOKEN_MISSING))
                    .verifyComplete();
            assertThat(verifier.received()).isEmpty();
        }

        @Test
        @DisplayName("Bearer 값만 검증기에 넘긴다")
        void passesTokenValue() {
            FakeTokenVerifier verifier = new FakeTokenVerifier(validToken(Role.USER));

            new AuthenticationService(POLICY, verifier, new FakeTokenStateReader())
                    .authenticate(request("GET", "/api/v1/orders", "Bearer abc.def.ghi")).block();

            assertThat(verifier.received()).containsExactly("abc.def.ghi");
        }

        @ParameterizedTest
        @EnumSource(value = AuthErrorCode.class, names = {"TOKEN_EXPIRED", "TOKEN_INVALID"})
        @DisplayName("검증 실패 코드를 그대로 결과에 담는다")
        void verificationFailure(AuthErrorCode code) {
            AuthenticationService service = new AuthenticationService(POLICY,
                    new FakeTokenVerifier(VerificationResult.failure(code)), new FakeTokenStateReader());

            StepVerifier.create(service.authenticate(request("GET", "/api/v1/orders", "Bearer t")))
                    .assertNext(result -> assertDenied(result, code))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("인가·헤더 (GW-06)")
    class Authorization {

        @Test
        @DisplayName("USER가 MASTER 경로를 호출하면 ACCESS_DENIED")
        void userOnMasterPath() {
            AuthenticationService service = new AuthenticationService(POLICY, new FakeTokenVerifier(validToken(Role.USER)), new FakeTokenStateReader());

            StepVerifier.create(service.authenticate(request("GET", "/api/v1/users", "Bearer t")))
                    .assertNext(result -> assertDenied(result, CommonErrorCode.ACCESS_DENIED))
                    .verifyComplete();
        }

        @Test
        @DisplayName("MASTER는 MASTER 경로를 통과한다")
        void masterOnMasterPath() {
            AuthenticationService service = new AuthenticationService(POLICY, new FakeTokenVerifier(validToken(Role.MASTER)), new FakeTokenStateReader());

            StepVerifier.create(service.authenticate(request("GET", "/api/v1/users", "Bearer t")))
                    .assertNext(result -> assertThat(result.allowed()).isTrue())
                    .verifyComplete();
        }

        @Test
        @DisplayName("USER도 본인 경로(/users/me)는 통과한다")
        void userOnMe() {
            AuthenticationService service = new AuthenticationService(POLICY, new FakeTokenVerifier(validToken(Role.USER)), new FakeTokenStateReader());

            StepVerifier.create(service.authenticate(request("GET", "/api/v1/users/me", "Bearer t")))
                    .assertNext(result -> assertThat(result.allowed()).isTrue())
                    .verifyComplete();
        }

        @Test
        @DisplayName("통과하면 하위 서비스에 보낼 헤더 4개를 담는다")
        void injectsHeaders() {
            AuthenticationService service = new AuthenticationService(POLICY, new FakeTokenVerifier(validToken(Role.USER)), new FakeTokenStateReader());

            StepVerifier.create(service.authenticate(request("GET", "/api/v1/orders", "Bearer t")))
                    .assertNext(result -> assertThat(result.headers()).containsOnly(
                            entry("X-User-Id", USER_ID.toString()),
                            entry("X-User-Role", "USER"),
                            entry("X-Token-Jti", "jti-1"),
                            entry("X-Token-Exp", String.valueOf(ISSUED_AT.plusSeconds(1800).getEpochSecond()))))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("토큰 상태·비밀번호 변경 강제 (GW-07)")
    class TokenStateCheck {

        private AuthResult authenticate(VerificationResult token, FakeTokenStateReader reader, String method, String path) {
            AuthenticationService service = new AuthenticationService(POLICY, new FakeTokenVerifier(token), reader);
            return service.authenticate(request(method, path, "Bearer t")).block();
        }

        @Test
        @DisplayName("4. 로그아웃(blacklist)한 토큰은 TOKEN_REVOKED")
        void blacklisted() {
            FakeTokenStateReader reader = new FakeTokenStateReader(Mono.just(new TokenState(true, Optional.empty(), false)));

            assertDenied(authenticate(validToken(Role.USER), reader, "GET", "/api/v1/orders"), AuthErrorCode.TOKEN_REVOKED);
        }

        @Test
        @DisplayName("4. 무효화(revoked-at) 이전에 발급된 토큰은 TOKEN_REVOKED, 이후 발급은 통과")
        void revokedAt() {
            FakeTokenStateReader before = new FakeTokenStateReader(
                    Mono.just(new TokenState(false, Optional.of(ISSUED_AT.plusSeconds(10)), false)));
            FakeTokenStateReader after = new FakeTokenStateReader(
                    Mono.just(new TokenState(false, Optional.of(ISSUED_AT.minusSeconds(10)), false)));

            assertDenied(authenticate(validToken(Role.USER), before, "GET", "/api/v1/orders"), AuthErrorCode.TOKEN_REVOKED);
            assertThat(authenticate(validToken(Role.USER), after, "GET", "/api/v1/orders").allowed()).isTrue();
        }

        @Test
        @DisplayName("5. 상태 저장소 오류면 AUTH_UNAVAILABLE (fail-closed: 로그아웃한 토큰이 통과하지 않게)")
        void readerError() {
            assertDenied(authenticate(validToken(Role.USER), FakeTokenStateReader.failing(), "GET", "/api/v1/orders"),
                    AuthErrorCode.AUTH_UNAVAILABLE);
        }

        @Test
        @DisplayName("5. 상태 저장소가 값 없이 끝나도(empty) AUTH_UNAVAILABLE")
        void readerEmpty() {
            assertDenied(authenticate(validToken(Role.USER), new FakeTokenStateReader(Mono.empty()), "GET", "/api/v1/orders"),
                    AuthErrorCode.AUTH_UNAVAILABLE);
        }

        @Test
        @DisplayName("6. 비밀번호 변경이 필요하면(pcr) 허용 경로 외에는 PASSWORD_CHANGE_REQUIRED, 허용 경로는 통과")
        void passwordChangeRequired() {
            VerificationResult pcr = validToken(Role.USER, true);

            assertDenied(authenticate(pcr, new FakeTokenStateReader(), "GET", "/api/v1/orders"),
                    AuthErrorCode.PASSWORD_CHANGE_REQUIRED);
            assertThat(authenticate(pcr, new FakeTokenStateReader(), "PATCH", "/api/v1/users/me/password").allowed()).isTrue();
            assertThat(authenticate(pcr, new FakeTokenStateReader(), "POST", "/api/v1/auth/logout").allowed()).isTrue();
        }

        @Test
        @DisplayName("7. 비밀번호 변경이 필요한 MASTER도 MASTER 경로보다 변경이 먼저 (ACCESS_DENIED가 아니라 PASSWORD_CHANGE_REQUIRED)")
        void passwordChangeBeforeMaster() {
            assertDenied(authenticate(validToken(Role.MASTER, true), new FakeTokenStateReader(), "GET", "/api/v1/users"),
                    AuthErrorCode.PASSWORD_CHANGE_REQUIRED);
            assertDenied(authenticate(validToken(Role.USER, true), new FakeTokenStateReader(), "GET", "/api/v1/users"),
                    AuthErrorCode.PASSWORD_CHANGE_REQUIRED);
        }

        @Test
        @DisplayName("무효화된 토큰은 비밀번호 변경 여부보다 먼저 TOKEN_REVOKED")
        void revokedBeforePasswordChange() {
            FakeTokenStateReader reader = new FakeTokenStateReader(Mono.just(new TokenState(true, Optional.empty(), false)));

            assertDenied(authenticate(validToken(Role.USER, true), reader, "PATCH", "/api/v1/users/me/password"),
                    AuthErrorCode.TOKEN_REVOKED);
        }

        @Test
        @DisplayName("8. 공개 경로·토큰 없음·서명 실패는 상태 저장소를 조회하지 않는다")
        void noReadWithoutValidToken() {
            FakeTokenStateReader reader = new FakeTokenStateReader();
            AuthenticationService valid = new AuthenticationService(POLICY, new FakeTokenVerifier(validToken(Role.USER)), reader);
            AuthenticationService invalid = new AuthenticationService(POLICY,
                    new FakeTokenVerifier(VerificationResult.failure(AuthErrorCode.TOKEN_INVALID)), reader);

            valid.authenticate(request("POST", "/api/v1/auth/login", "Bearer t")).block();
            valid.authenticate(request("GET", "/api/v1/orders", null)).block();
            invalid.authenticate(request("GET", "/api/v1/orders", "Bearer t")).block();

            assertThat(reader.reads()).isEmpty();
        }

        @Test
        @DisplayName("9. 보호 경로 요청 1번에 상태 저장소를 1번 조회한다 (토큰의 사용자 ID·jti로)")
        void oneReadPerRequest() {
            FakeTokenStateReader reader = new FakeTokenStateReader();

            authenticate(validToken(Role.USER), reader, "GET", "/api/v1/orders");

            assertThat(reader.reads()).containsExactly(USER_ID + "/jti-1");
        }
    }

    private static void assertDenied(AuthResult result, ErrorCode expected) {
        assertThat(result.allowed()).isFalse();
        assertThat(result.errorCode()).isEqualTo(expected);
        assertThat(result.headers()).isEmpty();
    }
}
