package com.gateway.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 서비스가 아직 등록되지 않았거나 모두 내려간 경우 (예: 기동 직후).
 * 응답 본문의 공통 오류 형식은 GW-04에서 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class NoInstanceTest {

    @Autowired
    WebTestClient client;

    @Test
    @DisplayName("대상 서비스 인스턴스가 하나도 없으면 503")
    void noInstance_isServiceUnavailable() {
        client.get().uri("/api/v1/orders")
                .exchange()
                .expectStatus().isEqualTo(503);
    }
}
