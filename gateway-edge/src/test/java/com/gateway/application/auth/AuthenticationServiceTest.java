package com.gateway.application.auth;

import com.common.exception.AuthErrorCode;
import com.common.exception.CommonErrorCode;
import com.common.exception.ErrorCode;
import com.gateway.application.auth.dto.AuthRequest;
import com.gateway.application.auth.dto.AuthResult;
import com.gateway.domain.policy.RoutePolicy;
import com.gateway.domain.token.AccessTokenClaims;
import com.gateway.domain.token.Role;
import com.gateway.domain.token.VerificationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
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
            .build();

    private static VerificationResult validToken(Role role) {
        return VerificationResult.success(new AccessTokenClaims(USER_ID, role, false, "jti-1",
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
            AuthenticationService service = new AuthenticationService(POLICY, verifier);

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
            AuthenticationService service = new AuthenticationService(POLICY, verifier);

            StepVerifier.create(service.authenticate(request("GET", "/api/v1/orders", null)))
                    .assertNext(result -> assertDenied(result, AuthErrorCode.TOKEN_MISSING))
                    .verifyComplete();
            assertThat(verifier.received()).isEmpty();
        }

        @Test
        @DisplayName("Bearer 값만 검증기에 넘긴다")
        void passesTokenValue() {
            FakeTokenVerifier verifier = new FakeTokenVerifier(validToken(Role.USER));

            new AuthenticationService(POLICY, verifier)
                    .authenticate(request("GET", "/api/v1/orders", "Bearer abc.def.ghi")).block();

            assertThat(verifier.received()).containsExactly("abc.def.ghi");
        }

        @ParameterizedTest
        @EnumSource(value = AuthErrorCode.class, names = {"TOKEN_EXPIRED", "TOKEN_INVALID"})
        @DisplayName("검증 실패 코드를 그대로 결과에 담는다")
        void verificationFailure(AuthErrorCode code) {
            AuthenticationService service = new AuthenticationService(POLICY,
                    new FakeTokenVerifier(VerificationResult.failure(code)));

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
            AuthenticationService service = new AuthenticationService(POLICY, new FakeTokenVerifier(validToken(Role.USER)));

            StepVerifier.create(service.authenticate(request("GET", "/api/v1/users", "Bearer t")))
                    .assertNext(result -> assertDenied(result, CommonErrorCode.ACCESS_DENIED))
                    .verifyComplete();
        }

        @Test
        @DisplayName("MASTER는 MASTER 경로를 통과한다")
        void masterOnMasterPath() {
            AuthenticationService service = new AuthenticationService(POLICY, new FakeTokenVerifier(validToken(Role.MASTER)));

            StepVerifier.create(service.authenticate(request("GET", "/api/v1/users", "Bearer t")))
                    .assertNext(result -> assertThat(result.allowed()).isTrue())
                    .verifyComplete();
        }

        @Test
        @DisplayName("USER도 본인 경로(/users/me)는 통과한다")
        void userOnMe() {
            AuthenticationService service = new AuthenticationService(POLICY, new FakeTokenVerifier(validToken(Role.USER)));

            StepVerifier.create(service.authenticate(request("GET", "/api/v1/users/me", "Bearer t")))
                    .assertNext(result -> assertThat(result.allowed()).isTrue())
                    .verifyComplete();
        }

        @Test
        @DisplayName("통과하면 하위 서비스에 보낼 헤더 4개를 담는다")
        void injectsHeaders() {
            AuthenticationService service = new AuthenticationService(POLICY, new FakeTokenVerifier(validToken(Role.USER)));

            StepVerifier.create(service.authenticate(request("GET", "/api/v1/orders", "Bearer t")))
                    .assertNext(result -> assertThat(result.headers()).containsOnly(
                            entry("X-User-Id", USER_ID.toString()),
                            entry("X-User-Role", "USER"),
                            entry("X-Token-Jti", "jti-1"),
                            entry("X-Token-Exp", String.valueOf(ISSUED_AT.plusSeconds(1800).getEpochSecond()))))
                    .verifyComplete();
        }
    }

    private static void assertDenied(AuthResult result, ErrorCode expected) {
        assertThat(result.allowed()).isFalse();
        assertThat(result.errorCode()).isEqualTo(expected);
        assertThat(result.headers()).isEmpty();
    }
}
