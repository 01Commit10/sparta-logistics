package com.gateway.request;

import com.gateway.support.TestTokenFactory;
import com.gateway.support.WireMockServices;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
 * GW-08: 하위 서비스가 받은 X-Request-Id = 응답 헤더의 X-Request-Id. Gateway가 직접 만든 오류 응답에도 붙는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ExtendWith(OutputCaptureExtension.class)
class RequestIdIntegrationTest {

    static final WireMockServices services = WireMockServices.start(List.of("hub-ms"));

    @DynamicPropertySource
    static void registerServices(DynamicPropertyRegistry registry) {
        services.registerInstances(registry);
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

    private String receivedRequestId() {
        List<LoggedRequest> requests = services.get("hub-ms").findAll(anyRequestedFor(anyUrl()));
        assertThat(requests).hasSize(1);
        return requests.get(0).getHeader("X-Request-Id");
    }

    @Test
    @DisplayName("7. 클라이언트가 보낸 ID가 하위 서비스와 응답 헤더에 그대로 간다")
    void clientId() {
        services.get("hub-ms").stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));

        client.get().uri("/api/v1/hubs").header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer("USER"))
                .header("X-Request-Id", "demo-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valuesMatch("X-Request-Id", "demo-123");

        assertThat(receivedRequestId()).isEqualTo("demo-123");
    }

    @Test
    @DisplayName("7. ID가 없거나 형식이 틀리면 새 UUID를 만들어 하위 서비스와 응답에 같은 값을 쓴다")
    void generatedId() {
        services.get("hub-ms").stubFor(any(anyUrl())
                .willReturn(aResponse().withStatus(200).withHeader("X-Request-Id", "downstream-id")));

        String responseId = client.get().uri("/api/v1/hubs").header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer("USER"))
                .header("X-Request-Id", "bad id with spaces")
                .exchange()
                .expectStatus().isOk()
                .returnResult(Void.class).getResponseHeaders().get("X-Request-Id").stream().reduce((a, b) -> a + "," + b).orElseThrow();

        assertThat(responseId).matches("[0-9a-f-]{36}");
        assertThat(receivedRequestId()).isEqualTo(responseId);
    }

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
            "GET,  /api/v1/hubs,                 401, false",   // 토큰 없음
            "GET,  /api/v1/users,                403, true",    // USER가 MASTER 경로
            "GET,  /api/v1/unknown,              404, false",   // 라우트 없음 (GlobalFilter는 실행되지 않음)
            "GET,  /internal/v1/hubs,            404, true",    // 내부 경로
    })
    @DisplayName("8. Gateway가 만든 오류 응답에도 X-Request-Id가 있다")
    void errorResponses(String method, String path, int status, boolean withToken) {
        WebTestClient.RequestHeadersSpec<?> request = client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(path)
                .header("X-Request-Id", "err-" + status);
        if (withToken) {
            request = request.header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer("USER"));
        }

        request.exchange()
                .expectStatus().isEqualTo(status)
                .expectHeader().valuesMatch("X-Request-Id", "err-" + status)
                .expectBody().jsonPath("$.code").isNotEmpty();
    }

    @Test
    @DisplayName("9. 접근 로그 1줄에 사용자 ID는 있고 Authorization 값과 쿼리스트링은 없다")
    void accessLog(CapturedOutput output) {
        services.get("hub-ms").stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
        String bearer = TestTokenFactory.bearer("USER");

        client.get().uri("/api/v1/hubs?keyword=secret-q").header(HttpHeaders.AUTHORIZATION, bearer)
                .header("X-Request-Id", "log-1")
                .exchange()
                .expectStatus().isOk();

        // 접근 로그는 응답을 보낸 뒤(doFinally) 다른 스레드에서 남는다
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(output.getOut()).contains(" log-1 "));
        assertThat(output.getOut())
                .contains("GET /api/v1/hubs 200 ")
                .contains(" log-1 " + TestTokenFactory.USER_ID)
                .doesNotContain(bearer.substring("Bearer ".length()))
                .doesNotContain("secret-q");
    }
}
