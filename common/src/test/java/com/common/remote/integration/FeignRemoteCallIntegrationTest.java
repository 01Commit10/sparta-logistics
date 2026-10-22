package com.common.remote.integration;

import com.common.auth.AuthContract;
import com.common.exception.UserErrorCode;
import com.common.remote.RemoteBusinessException;
import com.common.remote.RemoteCallTemplate;
import com.common.remote.RemoteServiceException;
import com.common.remote.RemoteTimeoutException;
import com.github.tomakehurst.wiremock.WireMockServer;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 Feign + LoadBalancer로 원격 호출 실패 분류를 확인한다 (C02 테스트 13·18·19, C03에서 Circuit Breaker·재시도 제거).
 * application-common.yaml의 값을 그대로 쓰고, read 타임아웃(300ms)만 테스트용으로 줄인다.
 */
@SpringBootTest(classes = RemoteTestApplication.class, properties =
        "spring.cloud.openfeign.client.config.default.read-timeout=300")
@AutoConfigureMockMvc
@ActiveProfiles("common")
class FeignRemoteCallIntegrationTest {

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
    MockMvc mockMvc;

    @BeforeEach
    void reset() {
        HUB.resetAll();
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
    @DisplayName("리뷰: 200인데 본문이 깨진 JSON·타입 불일치·HTML → RemoteTimeoutException (원격은 처리했으니 결과 모름)")
    void decodeFailures() {
        for (String[] body : new String[][]{{"application/json", "{\"id\":"},
                {"application/json", "{\"id\":\"not-uuid\",\"quantity\":1}"}, {"text/html", "<html>ok</html>"}}) {
            HUB.stubFor(get("/hub/item").willReturn(aResponse().withStatus(200)
                    .withHeader("Content-Type", body[0]).withBody(body[1])));

            assertThatThrownBy(() -> remote.execute("hub-ms", hubClient::item))
                    .as(body[1]).isInstanceOf(RemoteTimeoutException.class);
        }
    }

    @Test
    @DisplayName("4xx → 원래 code의 RemoteBusinessException, 다시 보내지 않는다 (요청 1번)")
    void businessError() {
        stubUnstable(409, "{\"code\":\"DUPLICATE_USERNAME\",\"message\":\"중복\"}");

        assertThatThrownBy(() -> remote.execute("hub-ms", hubClient::unstable))
                .isInstanceOfSatisfying(RemoteBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isSameAs(UserErrorCode.DUPLICATE_USERNAME));
        HUB.verify(1, getRequestedFor(urlEqualTo("/hub/unstable")));
    }

    @Test
    @DisplayName("5xx → RemoteServiceException, 다시 보내지 않는다 (요청 1번)")
    void serverError() {
        stubUnstable(500, "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"x\"}");

        assertThatThrownBy(() -> remote.execute("hub-ms", hubClient::unstable))
                .isInstanceOfSatisfying(RemoteServiceException.class, e -> assertThat(e.getStatus()).isEqualTo(500));
        HUB.verify(1, getRequestedFor(urlEqualTo("/hub/unstable")));
    }

    @Test
    @DisplayName("리뷰: 원격 504 → RemoteTimeoutException(status 504), 다시 보내지 않는다 (요청 1번)")
    void remoteGatewayTimeout() {
        stubUnstable(504, "{\"code\":\"SERVICE_TIMEOUT\",\"message\":\"응답 시간이 초과되었습니다.\"}");

        assertThatThrownBy(() -> remote.execute("hub-ms", hubClient::unstable))
                .isInstanceOfSatisfying(RemoteTimeoutException.class, e -> assertThat(e.getStatus()).isEqualTo(504));
        HUB.verify(1, getRequestedFor(urlEqualTo("/hub/unstable")));
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
        @DisplayName("18. 사용자 헤더를 전달하고 Authorization은 넘기지 않는다 (C01 테스트 18)")
        void relaysUserHeaders() throws Exception {
            String userId = UUID.randomUUID().toString();

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
        }

        @Test
        @DisplayName("19. 잡지 않은 원격 예외 → 공통 형식: 5xx는 503, 타임아웃·원격 504는 504, 4xx는 원래 상태·code")
        void uncaughtRemoteExceptions() throws Exception {
            var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/test/hub-unstable");

            stubUnstable(500, "");
            mockMvc.perform(request).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));

            HUB.stubFor(get("/hub/unstable").willReturn(aResponse().withStatus(200).withFixedDelay(1000)));
            mockMvc.perform(request).andExpect(status().isGatewayTimeout())
                    .andExpect(jsonPath("$.code").value("SERVICE_TIMEOUT"));

            stubUnstable(504, "{\"code\":\"SERVICE_TIMEOUT\",\"message\":\"x\"}");
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
