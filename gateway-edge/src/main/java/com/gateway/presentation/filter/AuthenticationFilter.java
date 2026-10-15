package com.gateway.presentation.filter;

import com.gateway.application.auth.AuthenticationService;
import com.gateway.application.auth.dto.AuthRequest;
import com.gateway.application.auth.dto.AuthResult;
import com.gateway.presentation.error.ErrorResponseWriter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 인증·인가 필터 (GW-05·06). 판단은 AuthenticationService가 하고, 이 필터는 결과만 적용한다.
 * <ul>
 *     <li>거부: 공통 오류 형식(401/403)으로 응답하고 라우팅하지 않는다</li>
 *     <li>통과: X-User-*, X-Token-* 헤더를 붙여 라우팅한다. Authorization은 그대로 전달한다</li>
 * </ul>
 * 외부에서 온 같은 이름의 헤더는 HeaderSanitizeFilter가 먼저 지운다.
 */
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final AuthenticationService authenticationService;
    private final ErrorResponseWriter errorResponseWriter;

    public AuthenticationFilter(AuthenticationService authenticationService, ErrorResponseWriter errorResponseWriter) {
        this.authenticationService = authenticationService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        AuthRequest authRequest = new AuthRequest(
                request.getMethod().name(),
                request.getPath().value(),
                request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        return authenticationService.authenticate(authRequest)
                .flatMap(result -> apply(exchange, chain, result));
    }

    private Mono<Void> apply(ServerWebExchange exchange, GatewayFilterChain chain, AuthResult result) {
        if (!result.allowed()) {
            return errorResponseWriter.write(exchange, result.errorCode());
        }
        if (result.headers().isEmpty()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest withUserHeaders = exchange.getRequest().mutate()
                .headers(headers -> result.headers().forEach(headers::set))
                .build();
        return chain.filter(exchange.mutate().request(withUserHeaders).build());
    }

    @Override
    public int getOrder() {
        return FilterOrder.AUTHENTICATION;
    }
}
