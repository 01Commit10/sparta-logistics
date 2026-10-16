package com.gateway.presentation.filter;

import com.common.exception.AuthErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.application.auth.AuthenticationService;
import com.gateway.domain.policy.RoutePolicy;
import com.gateway.domain.token.AccessTokenClaims;
import com.gateway.domain.token.Role;
import com.gateway.domain.token.TokenState;
import com.gateway.domain.token.VerificationResult;
import com.gateway.presentation.error.ErrorResponseWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationFilterTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant ISSUED_AT = Instant.parse("2026-10-15T00:00:00Z");

    private static final RoutePolicy POLICY = RoutePolicy.builder()
            .publicRules(List.of("POST /api/v1/auth/login"))
            .masterRules(List.of("GET /api/v1/users"))
            .build();

    private static AuthenticationFilter filterWith(VerificationResult verification) {
        return new AuthenticationFilter(
                new AuthenticationService(POLICY, token -> verification, (userId, jti) -> Mono.just(TokenState.active())),
                new ErrorResponseWriter(new ObjectMapper()));
    }

    private static VerificationResult valid(Role role) {
        return VerificationResult.success(new AccessTokenClaims(USER_ID, role, false, "jti-1", ISSUED_AT, ISSUED_AT.plusSeconds(1800)));
    }

    @Test
    @DisplayName("거부하면 공통 오류 형식으로 응답하고 체인을 부르지 않는다")
    void denied() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders"));
        CapturingChain chain = new CapturingChain();

        filterWith(VerificationResult.failure(AuthErrorCode.TOKEN_INVALID)).filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":\"TOKEN_MISSING\"");
    }

    @Test
    @DisplayName("USER가 MASTER 경로를 호출하면 403 ACCESS_DENIED")
    void forbidden() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer t"));
        CapturingChain chain = new CapturingChain();

        filterWith(valid(Role.USER)).filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":\"ACCESS_DENIED\"");
    }

    @Test
    @DisplayName("통과하면 사용자 헤더 4개를 붙여 체인에 넘기고, Authorization은 그대로 둔다")
    void passesWithHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer t"));
        CapturingChain chain = new CapturingChain();

        filterWith(valid(Role.USER)).filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isTrue();
        HttpHeaders headers = chain.passed().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo(USER_ID.toString());
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("USER");
        assertThat(headers.getFirst("X-Token-Jti")).isEqualTo("jti-1");
        assertThat(headers.getFirst("X-Token-Exp")).isEqualTo(String.valueOf(ISSUED_AT.plusSeconds(1800).getEpochSecond()));
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer t");
    }

    @Test
    @DisplayName("공개 경로는 헤더 없이 그대로 넘긴다")
    void publicPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login"));
        CapturingChain chain = new CapturingChain();

        filterWith(valid(Role.USER)).filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isTrue();
        assertThat(chain.passed().getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    @DisplayName("차단 필터(GW-03) 뒤, 라우팅 앞에서 실행된다")
    void order() {
        assertThat(filterWith(valid(Role.USER)).getOrder())
                .isGreaterThan(FilterOrder.BLOCKED_PATH)
                .isEqualTo(FilterOrder.AUTHENTICATION);
    }
}
