package com.gateway.resilience;

import com.gateway.support.TestTokenFactory;
import com.gateway.support.WireMockServices;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * GW-10: 서비스별 Circuit Breaker. test 프로필: 열림 대기 1초, 응답 타임아웃 1초(주문 3초).
 * 열림 조건(10건 중 5건 이상, 실패율 50%)은 운영과 같다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "10s")
class CircuitBreakerIntegrationTest {

    static final WireMockServices services = WireMockServices.start(List.of("hub-ms", "user-ms", "order-ms"));

    @DynamicPropertySource
    static void registerServices(DynamicPropertyRegistry registry) {
        services.registerInstances(registry);
    }

    @Autowired
    WebTestClient client;

    @Autowired
    CircuitBreakerRegistry registry;

    /** CB 상태는 컨텍스트에 남으므로 테스트마다 닫힌 상태로 되돌린다 */
    @BeforeEach
    void reset() {
        services.resetAll();
        registry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @AfterAll
    static void stop() {
        services.stopAll();
    }

    private WebTestClient.ResponseSpec get(String path) {
        return client.get().uri(path)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer("USER"))
                .header("X-Request-Id", "cb-1")
                .exchange();
    }

    private void respond(String service, int status) {
        services.get(service).stubFor(any(anyUrl()).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody("{\"code\":\"DOWNSTREAM_" + status + "\"}")));
    }

    private int received(String service) {
        return services.get(service).findAll(anyRequestedFor(anyUrl())).size();
    }

    private CircuitBreaker.State state(String name) {
        return registry.circuitBreaker(name).getState();
    }

    /** 타임아웃 5번 → 실패율 100%로 OPEN */
    private void openHubBreaker() {
        services.get("hub-ms").stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200).withFixedDelay(1200)));
        for (int i = 0; i < 5; i++) {
            get("/api/v1/hubs").expectStatus().isEqualTo(504);
        }
    }

    @Test
    @DisplayName("3. 타임아웃 5번 → OPEN, 6번째는 하위 서비스에 보내지 않고 바로 503 SERVICE_UNAVAILABLE")
    void opensAfterFailures() {
        openHubBreaker();

        long started = System.nanoTime();
        get("/api/v1/hubs").expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(500));
        assertThat(state("hub-ms")).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(received("hub-ms")).isEqualTo(5);
    }

    @Test
    @DisplayName("4. 하위 4xx·5xx 응답은 실패로 세지 않고 본문 그대로 전달한다 (10번 연속이어도 CLOSED)")
    void errorResponsesAreNotFailures() {
        respond("hub-ms", 409);
        for (int i = 0; i < 10; i++) {
            get("/api/v1/hubs").expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("DOWNSTREAM_409");
        }
        respond("hub-ms", 500);
        for (int i = 0; i < 10; i++) {
            get("/api/v1/hubs").expectStatus().isEqualTo(500).expectBody().jsonPath("$.code").isEqualTo("DOWNSTREAM_500");
        }
        respond("hub-ms", 503);
        get("/api/v1/hubs").expectStatus().isEqualTo(503).expectBody().jsonPath("$.code").isEqualTo("DOWNSTREAM_503");

        assertThat(state("hub-ms")).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(received("hub-ms")).isEqualTo(21);
    }

    @Test
    @DisplayName("5. OPEN 후 대기 시간(1초)이 지나면 HALF_OPEN, 시험 3건 성공 → CLOSED")
    void recovers() {
        openHubBreaker();
        respond("hub-ms", 200);

        await().atMost(Duration.ofSeconds(3)).until(() -> state("hub-ms") == CircuitBreaker.State.HALF_OPEN);
        for (int i = 0; i < 3; i++) {
            get("/api/v1/hubs").expectStatus().isOk();
        }

        assertThat(state("hub-ms")).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("5. HALF_OPEN 시험이 실패하면 다시 OPEN")
    void reopens() {
        openHubBreaker();

        await().atMost(Duration.ofSeconds(3)).until(() -> state("hub-ms") == CircuitBreaker.State.HALF_OPEN);
        for (int i = 0; i < 3; i++) {
            get("/api/v1/hubs").expectStatus().isEqualTo(504);
        }

        assertThat(state("hub-ms")).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("6. hub-ms가 OPEN이어도 user-ms 요청은 정상 (서비스별 인스턴스)")
    void isolatedPerService() {
        openHubBreaker();
        respond("user-ms", 200);

        get("/api/v1/users/me").expectStatus().isOk();

        assertThat(state("hub-ms")).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(state("user-ms")).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("7. 주문 라우트 2.5초 응답 → 200 (TimeLimiter 기본 1초가 먼저 끊지 않는다)")
    void timeLimiterDoesNotCutFirst() {
        services.get("order-ms").stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200).withFixedDelay(2500)));

        get("/api/v1/orders").expectStatus().isOk();
    }

    @Test
    @DisplayName("8. 열림 응답은 공통 형식이고 X-Request-Id가 있다")
    void openResponseFormat() {
        openHubBreaker();

        get("/api/v1/hubs").expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("X-Request-Id", "cb-1")
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.message").isEqualTo("일시적으로 서비스를 이용할 수 없습니다.");
    }
}
