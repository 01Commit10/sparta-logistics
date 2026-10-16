package com.gateway.presentation.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 필터 단위 테스트용 체인. 다음 단계로 넘어간 exchange를 기록한다.
 */
class CapturingChain implements GatewayFilterChain {

    private ServerWebExchange passed;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange) {
        this.passed = exchange;
        return Mono.empty();
    }

    boolean wasCalled() {
        return passed != null;
    }

    ServerWebExchange passed() {
        return passed;
    }
}
