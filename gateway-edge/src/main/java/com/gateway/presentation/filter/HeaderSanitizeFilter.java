package com.gateway.presentation.filter;

import com.gateway.application.request.RequestGuard;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 외부 요청에 들어 있는 신뢰 헤더(X-User-*, X-Token-*)를 지운다.
 * 사용자 헤더는 인증 필터가 토큰을 검증한 뒤에만 다시 넣는다 (GW-06).
 */
@Component
public class HeaderSanitizeFilter implements GlobalFilter, Ordered {

    private final RequestGuard requestGuard;

    public HeaderSanitizeFilter(RequestGuard requestGuard) {
        this.requestGuard = requestGuard;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(headers -> headers.keySet().removeIf(requestGuard::isTrustedHeader))
                .build();
        return chain.filter(exchange.mutate().request(sanitized).build());
    }

    @Override
    public int getOrder() {
        return FilterOrder.HEADER_SANITIZE;
    }
}
