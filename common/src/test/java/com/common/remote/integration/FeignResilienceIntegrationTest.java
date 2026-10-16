package com.common.remote.integration;

import com.common.auth.AuthContract;
import com.common.exception.UserErrorCode;
import com.common.remote.RemoteBusinessException;
import com.common.remote.RemoteCallTemplate;
import com.common.remote.RemoteServiceException;
import com.common.remote.RemoteTimeoutException;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 Feign + Circuit Breaker + LoadBalancer로 장애 대응을 확인한다 (C02 테스트 13~19).
 * application-common.yaml의 값을 그대로 쓰고, 시간 값(read 300ms, OPEN 대기 1초)만 테스트용으로 줄인다.
 */
@SpringBootTest(classes = RemoteTestApplication.class, properties = {
        "spring.cloud.openfeign.client.config.default.read-timeout=300",
        "resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state=1s"})
@AutoConfigureMockMvc
@ActiveProfiles("common")
class FeignResilienceIntegrationTest {

    static final WireMockServer HUB = new WireMockServer(options().dynamicPort());
    static final int CLOSED_PORT = closedPort();

    static {
        HUB.start();
    }

    @DynamicPropertySource
    static void instances(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.hub-ms[0].uri", () -> "http://localhost:" + HUB.port());
        registry.add("spring.cloud.discovery.client.simple.instances.company-ms[0].uri", () -> "http://localhost:" + CLOSED_PORT);
    }

    @AfterAll
    static void stop() {
        HUB.stop();
    }

    @Autowired
    HubTestClient hubClient;

    @Autowired
    CompanyTestClient companyClient;

    @Autowired
    OrderTestClient orderClient;

    @Autowired
    RemoteCallTemplate remote;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void reset() {
        HUB.resetAll();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        HUB.stubFor(get("/hub/ok").willReturn(aResponse().withStatus(200).withBody("ok")));
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void stubUnstable(int status, String body) {
        HUB.stubFor(get("/hub/unstable").willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    /** CB 이름: 기본 AlphanumericCircuitBreakerNameResolver가 Feign configKey("HubTestClient#unstable()")에서 영숫자만 남긴 값 */
    private CircuitBreaker.State unstableState() {
        assertThat(circuitBreakers.getAllCircuitBreakers()).extracting(CircuitBreaker::getName).contains("HubTestClientunstable");
        return circuitBreakers.circuitBreaker("HubTestClientunstable").getState();
    }

    private void callUnstableIgnoringFailure() {
        try {
            remote.execute("hub-ms", hubClient::unstable);
        } catch (RuntimeException ignored) {
            // 실패 횟수를 쌓기만 한다
        }
    }

    @Test
    @DisplayName("13. 응답이 read-timeout(300ms)보다 늦으면 RemoteTimeoutException (결과 모름)")
    void readTimeout() {
        HUB.stubFor(get("/hub/unstable").willReturn(aResponse().withStatus(200).withBody("late").withFixedDelay(1000)));

        long started = System.nanoTime();
        assertThatThrownBy(() -> remote.execute("hub-ms", hubClient::unstable))
                .isInstanceOfSatisfying(RemoteTimeoutException.class, e -> assertThat(e.getTarget()).isEqualTo("hub-ms"));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(900));
    }

    @Test
    @DisplayName("14. 5xx 5회 → CB OPEN → 다음 호출은 원격에 닿지 않고 RemoteServiceException")
    void opensOnServerErrors() {
        stubUnstable(500, "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"x\"}");

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> remote.execute("hub-ms", hubClient::unstable)).isInstanceOf(RemoteServiceException.class);
        }
        assertThat(unstableState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> remote.execute("hub-ms", hubClient::unstable))
                .isInstanceOfSatisfying(RemoteServiceException.class,
                        e -> assertThat(e.getCause()).isInstanceOf(CallNotPermittedException.class));
        HUB.verify(5, getRequestedFor(urlEqualTo("/hub/unstable")));
    }

    @Test
    @DisplayName("15. 4xx 10회는 장애가 아니므로 CB가 닫힌 채로 남고, 매번 원래 code의 RemoteBusinessException")
    void ignoresBusinessErrors() {
        stubUnstable(409, "{\"code\":\"DUPLICATE_USERNAME\",\"message\":\"중복\"}");

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> remote.execute("hub-ms", hubClient::unstable))
                    .isInstanceOfSatisfying(RemoteBusinessException.class,
                            e -> assertThat(e.getErrorCode()).isSameAs(UserErrorCode.DUPLICATE_USERNAME));
        }
        assertThat(unstableState()).isEqualTo(CircuitBreaker.State.CLOSED);
        HUB.verify(10, getRequestedFor(urlEqualTo("/hub/unstable")));
    }

    @Test
    @DisplayName("16. OPEN → 1초 뒤 HALF_OPEN → 성공 3회 → CLOSED")
    void recovers() {
        stubUnstable(503, "");
        for (int i = 0; i < 5; i++) {
            callUnstableIgnoringFailure();
        }
        assertThat(unstableState()).isEqualTo(CircuitBreaker.State.OPEN);

        await().atMost(Duration.ofSeconds(3)).until(() -> unstableState() == CircuitBreaker.State.HALF_OPEN);
        stubUnstable(200, "\"ok\"");
        for (int i = 0; i < 3; i++) {
            assertThat(remote.execute("hub-ms", hubClient::unstable)).isNotNull();
        }

        assertThat(unstableState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("17. CB는 메서드 단위: unstable()이 OPEN이어도 같은 서비스의 ok()는 정상 (C02 E1)")
    void perMethod() {
        stubUnstable(500, "");
        for (int i = 0; i < 5; i++) {
            callUnstableIgnoringFailure();
        }
        assertThat(unstableState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThat(remote.execute("hub-ms", hubClient::ok)).isEqualTo("ok");
    }

    @Test
    @DisplayName("연결 거부(듣는 프로세스 없음) → RemoteServiceException (처리 안 됨이 확실, C02 E5)")
    void connectionRefused() {
        assertThatThrownBy(() -> remote.execute("company-ms", companyClient::ok))
                .isInstanceOfSatisfying(RemoteServiceException.class, e -> assertThat(e.getTarget()).isEqualTo("company-ms"));
    }

    @Test
    @DisplayName("인스턴스 없음(Eureka 미등록) → RemoteServiceException")
    void noInstance() {
        assertThatThrownBy(() -> remote.execute("order-ms", orderClient::ok)).isInstanceOf(RemoteServiceException.class);
    }

    @Nested
    @DisplayName("요청 안에서 부를 때")
    class InRequest {

        @Test
        @DisplayName("18. CB를 켠 상태에서도 사용자 헤더를 전달하고, Feign은 요청 스레드에서 실행된다 (C01 테스트 18, C02 E2)")
        void relaysUserHeaders() throws Exception {
            String userId = UUID.randomUUID().toString();
            RemoteTestApplication.FEIGN_THREAD.set(null);

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/test/hub-ok")
                            .header(AuthContract.HEADER_USER_ID, userId)
                            .header(AuthContract.HEADER_USER_ROLE, "USER")
                            .header(AuthContract.HEADER_REQUEST_ID, "req-1")
                            .header("Authorization", "Bearer secret"))
                    .andExpect(status().isOk());

            HUB.verify(getRequestedFor(urlEqualTo("/hub/ok"))
                    .withHeader(AuthContract.HEADER_USER_ID, equalTo(userId))
                    .withHeader(AuthContract.HEADER_USER_ROLE, equalTo("USER"))
                    .withHeader(AuthContract.HEADER_REQUEST_ID, equalTo("req-1"))
                    .withoutHeader("Authorization"));
            assertThat(RemoteTestApplication.FEIGN_THREAD.get().getName()).doesNotContain("resilience4j", "pool");
        }

        @Test
        @DisplayName("19. 잡지 않은 원격 예외 → 공통 형식: 5xx는 503, 타임아웃은 504, 4xx는 원래 상태·code")
        void uncaughtRemoteExceptions() throws Exception {
            var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/test/hub-unstable");

            stubUnstable(500, "");
            mockMvc.perform(request).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));

            HUB.stubFor(get("/hub/unstable").willReturn(aResponse().withStatus(200).withFixedDelay(1000)));
            mockMvc.perform(request).andExpect(status().isGatewayTimeout())
                    .andExpect(jsonPath("$.code").value("SERVICE_TIMEOUT"));

            stubUnstable(409, "{\"code\":\"INSUFFICIENT_STOCK\",\"message\":\"재고가 부족합니다.\"}");
            mockMvc.perform(request).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                    .andExpect(jsonPath("$.message").value("재고가 부족합니다."))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hub-ms"))));
        }
    }
}
