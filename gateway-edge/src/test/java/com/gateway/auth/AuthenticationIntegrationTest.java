package com.gateway.auth;

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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * GW-05: 공개 경로 외에는 유효한 Access Token이 있어야 하위 서비스에 도착한다.
 * 거부한 요청은 하위 서비스에 한 번도 전달되지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AuthenticationIntegrationTest {

    static final WireMockServices services = WireMockServices.start(List.of("user-ms", "order-ms"));

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

    private void stubAll() {
        services.get("user-ms").stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
        services.get("order-ms").stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
    }

    private void expectError(WebTestClient.ResponseSpec response, int status, String code) {
        response.expectStatus().isEqualTo(status)
                .expectBody()
                .jsonPath("$.code").isEqualTo(code)
                .jsonPath("$.message").isNotEmpty();
        services.get("order-ms").verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("토큰 없이 보호 경로를 호출하면 401 TOKEN_MISSING, 서비스에 도착하지 않는다")
    void missingToken() {
        stubAll();

        expectError(client.get().uri("/api/v1/orders").exchange(), 401, "TOKEN_MISSING");
    }

    @Test
    @DisplayName("Bearer 형식이 아니면 401 TOKEN_MISSING")
    void notBearer() {
        stubAll();

        expectError(client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Basic abc").exchange(),
                401, "TOKEN_MISSING");
    }

    @Test
    @DisplayName("위조·형식 오류 토큰은 401 TOKEN_INVALID")
    void invalidToken() {
        stubAll();
        String forged = TestTokenFactory.accessSignedWith(TestTokenFactory.OTHER_SECRET,
                TestTokenFactory.MASTER_ID, "MASTER", false, Instant.now());

        expectError(client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer abc").exchange(),
                401, "TOKEN_INVALID");
        expectError(client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged).exchange(),
                401, "TOKEN_INVALID");
    }

    @Test
    @DisplayName("만료된 토큰은 401 TOKEN_EXPIRED")
    void expiredToken() {
        stubAll();
        String expired = TestTokenFactory.access(TestTokenFactory.USER_ID, "USER", false,
                Instant.now().minus(Duration.ofMinutes(31)));

        expectError(client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired).exchange(),
                401, "TOKEN_EXPIRED");
    }

    @Test
    @DisplayName("Refresh Token으로는 API를 호출할 수 없다 (401 TOKEN_INVALID)")
    void refreshTokenRejected() {
        stubAll();
        String refresh = TestTokenFactory.refresh(TestTokenFactory.USER_ID, Instant.now());

        expectError(client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + refresh).exchange(),
                401, "TOKEN_INVALID");
    }

    @Test
    @DisplayName("유효한 토큰이면 서비스에 도착하고 Authorization 헤더도 그대로 전달된다")
    void validToken() {
        stubAll();
        String bearer = TestTokenFactory.bearer("USER");

        client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer)
                .exchange()
                .expectStatus().isOk();

        services.get("order-ms").verify(1, getRequestedFor(urlPathEqualTo("/api/v1/orders"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bearer)));
    }

    @Test
    @DisplayName("공개 경로는 토큰 없이 도착한다")
    void publicPath() {
        stubAll();

        client.post().uri("/api/v1/auth/login").exchange().expectStatus().isOk();
        client.get().uri("/api/v1/auth/check-username?username=user01").exchange().expectStatus().isOk();

        services.get("user-ms").verify(1, postRequestedFor(urlPathEqualTo("/api/v1/auth/login")));
        services.get("user-ms").verify(1, getRequestedFor(urlPathEqualTo("/api/v1/auth/check-username")));
    }

    @Test
    @DisplayName("공개 경로라도 메서드가 다르면 토큰이 필요하다 (POST /auth/logout)")
    void logoutNeedsToken() {
        stubAll();

        client.post().uri("/api/v1/auth/logout").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("TOKEN_MISSING");
        services.get("user-ms").verify(0, anyRequestedFor(anyUrl()));
    }
}
