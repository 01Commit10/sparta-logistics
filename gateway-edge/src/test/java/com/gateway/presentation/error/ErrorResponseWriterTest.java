package com.gateway.presentation.error;

import com.common.exception.AuthErrorCode;
import com.common.exception.CommonErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ErrorResponseWriter writer = new ErrorResponseWriter(objectMapper);

    @Test
    @DisplayName("오류 코드의 상태와 공통 형식 JSON 본문을 쓴다")
    void writesStatusAndBody() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders"));

        writer.write(exchange, AuthErrorCode.TOKEN_EXPIRED).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(body.get("code").asText()).isEqualTo("TOKEN_EXPIRED");
        assertThat(body.get("message").asText()).isEqualTo(AuthErrorCode.TOKEN_EXPIRED.getMessage());
        assertThat(body.has("errors")).isFalse();   // 비어 있으면 생략 (common ErrorResponse 규칙)
    }

    @Test
    @DisplayName("이미 응답이 시작됐으면 아무것도 쓰지 않는다")
    void doesNothingWhenCommitted() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders"));
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().setComplete().block();

        writer.write(exchange, CommonErrorCode.INTERNAL_SERVER_ERROR).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
