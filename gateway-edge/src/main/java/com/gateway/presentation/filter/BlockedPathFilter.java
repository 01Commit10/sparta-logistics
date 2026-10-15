package com.gateway.presentation.filter;

import com.gateway.application.request.RequestGuard;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 내부 API(/internal/**)를 외부에서 막는다. 라우트가 실수로 추가되어도 도달하지 않게 하는 방어선.
 * 403이 아니라 404로 응답해 내부 경로의 존재를 알리지 않는다.
 * 응답 본문의 공통 오류 형식은 GW-04에서 적용한다.
 */
@Component
public class BlockedPathFilter implements GlobalFilter, Ordered {

    private final RequestGuard requestGuard;

    public BlockedPathFilter(RequestGuard requestGuard) {
        this.requestGuard = requestGuard;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (requestGuard.isBlocked(exchange.getRequest().getPath().value())) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return FilterOrder.BLOCKED_PATH;
    }
}
