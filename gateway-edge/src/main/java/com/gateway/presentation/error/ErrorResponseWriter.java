package com.gateway.presentation.error;

import com.common.exception.ErrorCode;
import com.common.exception.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Gateway가 직접 만드는 모든 오류 응답을 서비스와 같은 공통 형식으로 쓴다.
 * <pre>{ "code": "TOKEN_EXPIRED", "message": "인증 토큰이 만료되었습니다." }</pre>
 * 필터가 요청을 거부할 때와 라우팅 중 예외(GatewayErrorWebExceptionHandler) 모두 이 클래스를 쓴다.
 */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, ErrorCode errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(errorCode.getHttpStatus());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = serialize(errorCode);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private byte[] serialize(ErrorCode errorCode) {
        try {
            return objectMapper.writeValueAsBytes(new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of()));
        } catch (JsonProcessingException e) {
            return ("{\"code\":\"" + errorCode.getCode() + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }
}
