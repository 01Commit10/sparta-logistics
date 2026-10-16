package com.gateway.presentation.filter;

import com.common.auth.AuthContract;
import com.gateway.application.request.RequestTracer;
import com.gateway.presentation.error.GatewayErrorWebExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.time.Clock;

/**
 * 요청 ID 부여·전달과 접근 로그 (GW-08).
 * <ul>
 *     <li>X-Request-Id: 형식이 맞는 외부 값은 그대로, 아니면 새 UUID. 하위 서비스 요청과 응답 헤더에 같은 값을 넣는다</li>
 *     <li>접근 로그: 요청이 끝나면 1줄. 쿼리스트링·Authorization은 남기지 않는다</li>
 * </ul>
 * GlobalFilter가 아니라 WebFilter다. GlobalFilter는 라우트에 매칭된 요청에서만 실행되어 404(라우트 없음)에 ID가 붙지 않는다.
 * 체인 예외는 바깥 핸들러로 보내지 않고 여기서 공통 오류 응답으로 바꾼다. 바깥 핸들러는 이 필터가 끝난 뒤에 실행되어,
 * 그대로 두면 로그에 실제 응답 상태(503·504)가 아니라 아직 쓰이지 않은 상태가 찍힌다.
 */
@Slf4j
@Component
public class RequestIdFilter implements WebFilter, Ordered {

    private final RequestTracer requestTracer;
    private final GatewayErrorWebExceptionHandler errorHandler;
    private final Clock clock;

    public RequestIdFilter(RequestTracer requestTracer, GatewayErrorWebExceptionHandler errorHandler, Clock clock) {
        this.requestTracer = requestTracer;
        this.errorHandler = errorHandler;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startedAt = clock.millis();
        String requestId = requestTracer.resolveRequestId(
                exchange.getRequest().getHeaders().getFirst(AuthContract.HEADER_REQUEST_ID));
        ServerWebExchange traced = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(AuthContract.HEADER_REQUEST_ID, requestId)))
                .build();
        // 하위 응답 헤더를 복사한 뒤에도 Gateway ID 하나만 남도록 커밋 직전에 덮어쓴다
        traced.getResponse().beforeCommit(() -> {
            traced.getResponse().getHeaders().set(AuthContract.HEADER_REQUEST_ID, requestId);
            return Mono.empty();
        });
        return chain.filter(traced)
                .onErrorResume(error -> errorHandler.handle(traced, error))
                .doFinally(signal -> writeAccessLog(traced, requestId, startedAt, signal));
    }

    private void writeAccessLog(ServerWebExchange exchange, String requestId, long startedAt, SignalType signal) {
        try {
            log.info(requestTracer.accessLogLine(
                    exchange.getRequest().getMethod().name(),
                    exchange.getRequest().getPath().value(),
                    status(exchange.getResponse(), signal),
                    clock.millis() - startedAt,
                    requestId,
                    exchange.getAttribute(GatewayAttributes.USER_ID)));
        } catch (RuntimeException e) {
            log.debug("접근 로그 기록 실패", e);   // 로그 실패가 요청에 영향을 주지 않게 한다
        }
    }

    /** 응답 전에 끊기면(CANCEL, 미커밋) 상태를 알 수 없다 */
    private static Integer status(ServerHttpResponse response, SignalType signal) {
        if (signal == SignalType.CANCEL && !response.isCommitted()) {
            return null;
        }
        HttpStatusCode status = response.getStatusCode();
        return status == null ? null : status.value();
    }

    @Override
    public int getOrder() {
        return FilterOrder.REQUEST_ID;
    }
}
