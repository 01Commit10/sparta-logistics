package com.gateway.application.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTracerTest {

    private final RequestTracer tracer = new RequestTracer();

    @Test
    @DisplayName("요청 ID 판단을 도메인 규칙에 맡긴다: 형식이 맞으면 그대로, 아니면 UUID")
    void resolveRequestId() {
        assertThat(tracer.resolveRequestId("demo-123")).isEqualTo("demo-123");
        assertThat(tracer.resolveRequestId("<script>")).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(tracer.resolveRequestId(null)).isNotEqualTo(tracer.resolveRequestId(null));
    }

    @Test
    @DisplayName("접근 로그 형식을 도메인 규칙에 맡긴다")
    void accessLogLine() {
        assertThat(tracer.accessLogLine("GET", "/api/v1/hubs?q=1", 200, 5, "demo-123", null))
                .isEqualTo("GET /api/v1/hubs 200 5 demo-123 -");
    }
}
