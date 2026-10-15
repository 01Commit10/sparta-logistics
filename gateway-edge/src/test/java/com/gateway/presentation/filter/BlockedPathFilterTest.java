package com.gateway.presentation.filter;

import com.gateway.application.request.RequestGuard;
import com.gateway.domain.policy.RoutePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlockedPathFilterTest {

    private final BlockedPathFilter filter = new BlockedPathFilter(new RequestGuard(
            RoutePolicy.builder().blocked(List.of("/internal/**", "/api/v1/internal/**")).build()));

    @Test
    @DisplayName("내부 API 경로는 404로 끝내고 다음 단계로 넘기지 않는다")
    void blocksInternalPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/internal/v1/users/1"));
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("그 외 경로는 그대로 넘긴다")
    void passesOtherPaths() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders"));
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
