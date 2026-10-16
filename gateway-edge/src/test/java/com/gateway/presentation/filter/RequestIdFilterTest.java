package com.gateway.presentation.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.application.request.RequestTracer;
import com.gateway.presentation.error.ErrorResponseWriter;
import com.gateway.presentation.error.ExceptionErrorMapper;
import com.gateway.presentation.error.GatewayErrorWebExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class RequestIdFilterTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    private final RequestIdFilter filter = new RequestIdFilter(
            new RequestTracer(),
            new GatewayErrorWebExceptionHandler(new ExceptionErrorMapper(), new ErrorResponseWriter(new ObjectMapper())),
            Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), ZoneOffset.UTC));

    /** 다음 단계로 넘어간 exchange를 기록하고 응답을 완료하는 체인 */
    private static final class CompletingChain implements WebFilterChain {
        private final AtomicReference<ServerWebExchange> passed = new AtomicReference<>();
        private final HttpStatus status;

        CompletingChain(HttpStatus status) {
            this.status = status;
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            passed.set(exchange);
            exchange.getResponse().setStatusCode(status);
            return exchange.getResponse().setComplete();
        }
    }

    @Test
    @DisplayName("5. 체인 요청과 응답 헤더에 같은 X-Request-Id를 넣는다 (형식이 맞는 외부 값은 그대로)")
    void propagatesId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/hubs").header("X-Request-Id", "demo-123"));
        CompletingChain chain = new CompletingChain(HttpStatus.OK);

        filter.filter(exchange, chain).block();

        assertThat(chain.passed.get().getRequest().getHeaders().get("X-Request-Id")).containsExactly("demo-123");
        assertThat(exchange.getResponse().getHeaders().get("X-Request-Id")).containsExactly("demo-123");
    }

    @Test
    @DisplayName("5. 형식이 틀린 외부 ID는 버리고 새로 만든 ID를 요청·응답에 똑같이 넣는다")
    void replacesInvalidId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/hubs").header("X-Request-Id", "<script>alert(1)</script>"));
        CompletingChain chain = new CompletingChain(HttpStatus.OK);

        filter.filter(exchange, chain).block();

        String forwarded = chain.passed.get().getRequest().getHeaders().getFirst("X-Request-Id");
        assertThat(forwarded).matches("[0-9a-f-]{36}");
        assertThat(exchange.getResponse().getHeaders().get("X-Request-Id")).containsExactly(forwarded);
    }

    @Test
    @DisplayName("하위 응답에 X-Request-Id가 있어도 응답 헤더는 Gateway ID 하나다")
    void overridesDownstreamHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/hubs").header("X-Request-Id", "demo-123"));

        filter.filter(exchange, ex -> {
            ex.getResponse().getHeaders().add("X-Request-Id", "downstream-id");
            return ex.getResponse().setComplete();
        }).block();

        assertThat(exchange.getResponse().getHeaders().get("X-Request-Id")).containsExactly("demo-123");
    }

    @Test
    @DisplayName("성공하면 접근 로그 1줄: method path status durationMs requestId userId (쿼리스트링 없음)")
    void logsSuccess(CapturedOutput output) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/hubs?keyword=secret-q").header("X-Request-Id", "demo-123"));

        filter.filter(exchange, ex -> {
            ex.getAttributes().put(GatewayAttributes.USER_ID, USER_ID);
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return ex.getResponse().setComplete();
        }).block();

        assertThat(output).contains("GET /api/v1/hubs 200 0 demo-123 " + USER_ID).doesNotContain("secret-q");
    }

    @Test
    @DisplayName("6. 체인이 오류로 끝나면 공통 오류 응답을 쓰고, 로그에 실제 응답 상태(504)를 1줄 남긴다")
    void logsError(CapturedOutput output) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/hubs").header("X-Request-Id", "err-1"));

        filter.filter(exchange, ex -> Mono.error(new TimeoutException("slow"))).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":\"SERVICE_TIMEOUT\"");
        assertThat(exchange.getResponse().getHeaders().get("X-Request-Id")).containsExactly("err-1");
        assertThat(output.getOut().lines().filter(line -> line.contains("GET /api/v1/hubs 504 0 err-1 -"))).hasSize(1);
    }

    @Test
    @DisplayName("필터가 직접 거부한 응답(401)도 그 상태로 기록한다")
    void logsRejected(CapturedOutput output) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/orders").header("X-Request-Id", "rej-1"));

        filter.filter(exchange, new CompletingChain(HttpStatus.UNAUTHORIZED)).block();

        assertThat(output).contains("POST /api/v1/orders 401 0 rej-1 -");
    }

    @Test
    @DisplayName("응답 전에 클라이언트가 끊으면 상태는 - (정해 둔 상태가 있어도 실제로 나가지 않았다)")
    void logsCancel(CapturedOutput output) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/hubs").header("X-Request-Id", "cancel-1"));

        filter.filter(exchange, ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.never();
        }).subscribe().dispose();

        assertThat(output).contains("GET /api/v1/hubs - 0 cancel-1 -");
    }

    @Test
    @DisplayName("가장 먼저 실행된다")
    void order() {
        assertThat(filter.getOrder()).isEqualTo(FilterOrder.REQUEST_ID).isLessThan(FilterOrder.HEADER_SANITIZE);
    }
}
