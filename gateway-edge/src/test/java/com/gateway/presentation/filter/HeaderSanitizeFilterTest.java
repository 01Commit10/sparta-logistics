package com.gateway.presentation.filter;

import com.gateway.application.request.RequestGuard;
import com.gateway.domain.policy.RoutePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderSanitizeFilterTest {

    private final HeaderSanitizeFilter filter =
            new HeaderSanitizeFilter(new RequestGuard(RoutePolicy.builder().build()));

    @Test
    @DisplayName("외부에서 보낸 X-User-*, X-Token-* 헤더를 지우고 나머지는 그대로 넘긴다")
    void removesTrustedHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/auth/login")
                .header("X-User-Id", "00000000-0000-0000-0000-000000000001")
                .header("x-user-role", "MASTER")
                .header("X-Token-Jti", "forged")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .header("Idempotency-Key", "k-1")
                .header("X-Request-Id", "r-1"));
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        HttpHeaders forwarded = chain.passed().getRequest().getHeaders();
        assertThat(forwarded.keySet()).noneMatch(name -> name.toLowerCase().startsWith("x-user-")
                || name.toLowerCase().startsWith("x-token-"));
        assertThat(forwarded.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token");
        assertThat(forwarded.getFirst("Idempotency-Key")).isEqualTo("k-1");
        assertThat(forwarded.getFirst("X-Request-Id")).isEqualTo("r-1");
    }

    @Test
    @DisplayName("같은 헤더가 여러 번 와도 모두 지운다")
    void removesAllValues() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/orders")
                .header("X-User-Role", "USER", "MASTER"));
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.passed().getRequest().getHeaders().get("X-User-Role")).isNull();
    }

    @Test
    @DisplayName("헤더 제거는 인증 필터보다 먼저 실행된다")
    void runsEarly() {
        assertThat(filter.getOrder()).isEqualTo(FilterOrder.HEADER_SANITIZE);
        assertThat(FilterOrder.HEADER_SANITIZE).isLessThan(FilterOrder.BLOCKED_PATH);
    }
}
