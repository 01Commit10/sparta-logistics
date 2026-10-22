package com.gateway.presentation.error;

import com.common.auth.AuthContract;
import com.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 라우팅 중 처리되지 않은 예외(라우트 없음, 인스턴스 없음, 연결 실패, 타임아웃 등)를 공통 형식으로 응답한다.
 * Spring Boot 기본 핸들러(@Order(-1))보다 먼저 실행되어 스택 트레이스·내부 주소가 응답에 나가지 않게 한다.
 * 하위 서비스가 보낸 오류 응답(예: 409)은 예외가 아니므로 여기를 거치지 않고 그대로 전달된다.
 */
@Slf4j
@Order(-2)
@Component
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ExceptionErrorMapper mapper;
    private final ErrorResponseWriter writer;

    public GatewayErrorWebExceptionHandler(ExceptionErrorMapper mapper, ErrorResponseWriter writer) {
        this.mapper = mapper;
        this.writer = writer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }
        ErrorCode errorCode = mapper.map(error);
        String path = exchange.getRequest().getPath().value();
        if (errorCode.getHttpStatus().is5xxServerError()) {
            log.warn("[GatewayError] {} {} → {} ({}) requestId={}", exchange.getRequest().getMethod(), path, errorCode.getCode(),
                    error.toString(), exchange.getRequest().getHeaders().getFirst(AuthContract.HEADER_REQUEST_ID));
        } else {
            log.debug("[GatewayError] {} {} → {}", exchange.getRequest().getMethod(), path, errorCode.getCode());
        }
        return writer.write(exchange, errorCode);
    }
}
