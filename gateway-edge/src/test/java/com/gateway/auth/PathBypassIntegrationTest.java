package com.gateway.auth;

import com.gateway.support.TestTokenFactory;
import com.gateway.support.WireMockServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.URI;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * 경로를 다르게 적어 MASTER 규칙·내부 경로 차단을 피하려는 요청.
 * 라우트(PathPattern)와 하위 서비스(Tomcat)는 디코딩·;제거 후 경로로 처리하므로, 정책도 같은 경로로 판단하거나 거부해야 한다.
 * URI는 인코딩이 바뀌지 않도록 그대로(URI.create) 보낸다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class PathBypassIntegrationTest {

    static final WireMockServices services = WireMockServices.startAll();

    @DynamicPropertySource
    static void registerServices(DynamicPropertyRegistry registry) {
        services.registerInstances(registry);
    }

    @Autowired
    WebTestClient client;

    @LocalServerPort
    int port;

    @BeforeEach
    void stubAll() {
        WireMockServices.SERVICES.forEach(name ->
                services.get(name).stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200))));
    }

    @AfterEach
    void reset() {
        services.resetAll();
    }

    @AfterAll
    static void stop() {
        services.stopAll();
    }

    private WebTestClient.ResponseSpec send(String method, String rawPath, String role) {
        return client.method(HttpMethod.valueOf(method)).uri(URI.create("http://localhost:" + port + rawPath))
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(role))
                .exchange();
    }

    private void verifyNothingArrived() {
        WireMockServices.SERVICES.forEach(name -> services.get(name).verify(0, anyRequestedFor(anyUrl())));
    }

    @ParameterizedTest(name = "{0} {1} → 403")
    @CsvSource({
            "GET,    /api/v1/%75sers/00000000-0000-0000-0000-000000000001",
            "GET,    /api/v1/%75%73%65%72%73",
            "PATCH,  /api/v1/hub%73/h-1",
            "DELETE, /api/v1/companie%73/c-1",
            "POST,   /api/v1/hub-route%73",
            "HEAD,   /api/v1/users/00000000-0000-0000-0000-000000000001",
            "HEAD,   /api/v1/users",
    })
    @DisplayName("인코딩·HEAD로 MASTER 경로를 호출해도 USER는 403 ACCESS_DENIED, 서비스에 도착하지 않는다")
    void encodedOrHead_isForbidden(String method, String rawPath) {
        send(method, rawPath, "USER").expectStatus().isForbidden();

        verifyNothingArrived();
    }

    @ParameterizedTest(name = "{0} {1} → 400")
    @CsvSource({
            "GET,    /api/v1/users;x/00000000-0000-0000-0000-000000000001",
            "GET,    /api/v1/users/u-1;jsessionid=a",
            "PATCH,  /api/v1/hubs;/h-1",
            "GET,    /api/v1/users/%2e%2e/%2e%2e/%2e%2e/internal/v1/users/1",
            "GET,    /api/v1/users/%2E%2E/%2E%2E/%2E%2E/internal/v1/users/1",
            "GET,    /api/v1/users/me%2f..%2f00000000-0000-0000-0000-000000000001",
            "GET,    /api/v1/users/me%5c..%5c00000000-0000-0000-0000-000000000001",
            "GET,    /api/v1/users/me/../00000000-0000-0000-0000-000000000001",
            "GET,    /api/v1/orders/%00",
    })
    @DisplayName("해석이 달라질 수 있는 경로는 MASTER 토큰이어도 400 INVALID_INPUT, 서비스에 도착하지 않는다")
    void ambiguousPath_isRejected(String method, String rawPath) {
        send(method, rawPath, "MASTER").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_INPUT");

        verifyNothingArrived();
    }

    @Test
    @DisplayName("이중 인코딩(%2575)은 라우트에 매칭되지 않거나 거부되어 서비스에 도착하지 않는다")
    void doubleEncoded_neverArrives() {
        send("GET", "/api/v1/%2575sers/u-1", "USER").expectStatus().is4xxClientError();

        verifyNothingArrived();
    }

    @Test
    @DisplayName("정상 요청의 인코딩(한글 등)은 그대로 통과하고 원래 형태로 전달된다")
    void legitimateEncoding_passes() {
        send("GET", "/api/v1/hubs/%ED%97%88%EB%B8%8C", "USER").expectStatus().isOk();

        services.get("hub-ms").verify(1, getRequestedFor(urlEqualTo("/api/v1/hubs/%ED%97%88%EB%B8%8C")));
    }

    @Test
    @DisplayName("MASTER는 인코딩된 MASTER 경로도 통과한다 (거부가 아니라 같은 경로로 판단)")
    void masterOnEncodedPath_passes() {
        send("GET", "/api/v1/%75sers/u-1", "MASTER").expectStatus().isOk();

        services.get("user-ms").verify(1, anyRequestedFor(anyUrl()));
    }
}
