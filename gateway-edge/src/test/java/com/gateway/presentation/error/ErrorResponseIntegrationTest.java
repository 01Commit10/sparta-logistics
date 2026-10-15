package com.gateway.presentation.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gateway가 직접 만드는 오류는 서비스와 같은 공통 형식이다 (SYS-01 6장).
 * 하위 서비스가 보낸 오류 본문은 바꾸지 않는다 (RoutingIntegrationTest에서 확인).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ErrorResponseIntegrationTest {

    @Autowired
    WebTestClient client;

    @Test
    @DisplayName("없는 경로 → 404 RESOURCE_NOT_FOUND")
    void unknownPath() {
        client.get().uri("/api/v1/unknown")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("RESOURCE_NOT_FOUND")
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.errors").doesNotExist();
    }

    @Test
    @DisplayName("대상 서비스 인스턴스 없음 → 503 SERVICE_UNAVAILABLE")
    void noInstance() {
        client.get().uri("/api/v1/orders")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("내부 API 경로 → 404 RESOURCE_NOT_FOUND (라우트가 없으므로 필터 전에 라우트 없음으로 처리됨)")
    void blockedPath() {
        client.get().uri("/internal/v1/users/00000000-0000-0000-0000-000000000001")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("오류 본문에 스택 트레이스·예외 이름·내부 주소가 없다")
    void hidesInternals() {
        String body = client.get().uri("/api/v1/orders")
                .exchange()
                .expectBody(String.class)
                .returnResult().getResponseBody();

        assertThat(body)
                .doesNotContain("trace", "exception", "Exception", "lb://", "order-ms", "localhost", "path", "timestamp");
    }
}
