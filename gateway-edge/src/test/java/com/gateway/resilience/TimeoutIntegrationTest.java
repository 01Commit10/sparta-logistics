package com.gateway.resilience;

import com.gateway.support.TestTokenFactory;
import com.gateway.support.WireMockServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * GW-09: 하위 서비스가 늦으면 504, 연결이 안 되면 503. test 프로필 값: 전역 응답 1초, 주문 3초, 연결 2초.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "10s")
class TimeoutIntegrationTest {

    static final WireMockServices services = WireMockServices.start(List.of("hub-ms", "order-ms"));
    /** 아무도 듣지 않는 포트 (연결 거부) */
    static final int CLOSED_PORT = closedPort();

    @DynamicPropertySource
    static void registerServices(DynamicPropertyRegistry registry) {
        services.registerInstances(registry);
        registry.add("spring.cloud.discovery.client.simple.instances.company-ms[0].uri", () -> "http://localhost:" + CLOSED_PORT);
    }

    @Autowired
    WebTestClient client;

    @AfterEach
    void reset() {
        services.resetAll();
    }

    @AfterAll
    static void stop() {
        services.stopAll();
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void delay(String service, int millis) {
        services.get(service).stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200).withFixedDelay(millis)));
    }

    private WebTestClient.ResponseSpec get(String path) {
        return client.get().uri(path)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer("USER"))
                .header("X-Request-Id", "timeout-1")
                .exchange();
    }

    @Test
    @DisplayName("2. 일반 라우트가 1.5초 늦으면 504 SERVICE_TIMEOUT")
    void slow_isGatewayTimeout() {
        delay("hub-ms", 1500);

        get("/api/v1/hubs").expectStatus().isEqualTo(504)
                .expectBody().jsonPath("$.code").isEqualTo("SERVICE_TIMEOUT");
    }

    @Test
    @DisplayName("3. 일반 라우트가 0.5초 늦으면 200")
    void withinTimeout_isOk() {
        delay("hub-ms", 500);

        get("/api/v1/hubs").expectStatus().isOk();
    }

    @Test
    @DisplayName("4. 주문 라우트는 2초 늦어도 200 (전역 1초가 아니라 주문 3초 적용)")
    void orderRoute_hasLongerTimeout() {
        delay("order-ms", 2000);

        get("/api/v1/orders").expectStatus().isOk();
    }

    @Test
    @DisplayName("4. 주문 라우트도 3.5초 늦으면 504")
    void orderRoute_slow_isGatewayTimeout() {
        delay("order-ms", 3500);

        get("/api/v1/orders").expectStatus().isEqualTo(504);
    }

    @Test
    @DisplayName("5. 연결이 거부되면 연결 타임아웃을 기다리지 않고 503 SERVICE_UNAVAILABLE")
    void connectionRefused_isServiceUnavailable() {
        long started = System.nanoTime();

        get("/api/v1/companies").expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(2000 + 500));
    }

    @Test
    @DisplayName("6. 504 응답 본문은 공통 형식이고 X-Request-Id가 있다")
    void timeoutResponse_format() {
        delay("hub-ms", 1500);

        get("/api/v1/hubs").expectStatus().isEqualTo(504)
                .expectHeader().valueEquals("X-Request-Id", "timeout-1")
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_TIMEOUT")
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.trace").doesNotExist()
                .jsonPath("$.path").doesNotExist();
    }
}
