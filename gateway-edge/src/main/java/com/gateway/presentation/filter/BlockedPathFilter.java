package com.gateway.presentation.filter;

import com.common.exception.CommonErrorCode;
import com.gateway.application.request.RequestGuard;
import com.gateway.presentation.error.ErrorResponseWriter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * 내부 API(/internal/**)를 외부에서 막는다. 라우트가 실수로 추가되어도 도달하지 않게 하는 방어선.
 * 403이 아니라 404로 응답해 내부 경로의 존재를 알리지 않는다.
 * 전역 필터는 라우트에 매칭된 요청에서만 실행되므로, 이 필터는 내부 경로가 실수로 라우트에 들어간 경우를 막는다.
 * (라우트가 없으면 GatewayErrorWebExceptionHandler가 같은 404 RESOURCE_NOT_FOUND로 응답한다.)
 * 경로는 디코딩한 값으로 판단하고(/%69nternal도 차단), 해석이 달라질 수 있는 경로는 먼저 400 INVALID_INPUT (RequestPath).
 */
@Component
public class BlockedPathFilter implements GlobalFilter, Ordered {

    private final RequestGuard requestGuard;
    private final ErrorResponseWriter errorResponseWriter;

    public BlockedPathFilter(RequestGuard requestGuard, ErrorResponseWriter errorResponseWriter) {
        this.requestGuard = requestGuard;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Optional<String> path = requestGuard.policyPath(exchange.getRequest().getPath().value());
        if (path.isEmpty()) {
            return errorResponseWriter.write(exchange, CommonErrorCode.INVALID_INPUT);
        }
        if (requestGuard.isBlocked(path.get())) {
            return errorResponseWriter.write(exchange, CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return FilterOrder.BLOCKED_PATH;
    }
}
