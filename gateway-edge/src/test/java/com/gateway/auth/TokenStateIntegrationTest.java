package com.gateway.auth;

import com.gateway.support.InMemoryTokenStateReader;
import com.gateway.support.TestTokenFactory;
import com.gateway.support.WireMockServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;

/**
 * GW-07 테스트 13: 서명이 유효해도 로그아웃·무효화된 토큰과 비밀번호 변경 전 요청은 서비스에 도착하지 않는다.
 * 토큰 상태는 InMemoryTokenStateReader (실제 Redis는 TokenStateRedisIntegrationTest).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class TokenStateIntegrationTest {

    static final WireMockServices services = WireMockServices.start(List.of("user-ms", "order-ms"));

    @DynamicPropertySource
    static void registerServices(DynamicPropertyRegistry registry) {
        services.registerInstances(registry);
    }

    @Autowired
    WebTestClient client;

    @Autowired
    InMemoryTokenStateReader tokenState;

    @BeforeEach
    void stub() {
        services.get("user-ms").stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
        services.get("order-ms").stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
    }

    @AfterEach
    void reset() {
        services.resetAll();
        tokenState.clear();
    }

    @AfterAll
    static void stop() {
        services.stopAll();
    }

    private WebTestClient.ResponseSpec getOrders(String token) {
        return client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    private void expectRejected(WebTestClient.ResponseSpec response, int status, String code) {
        response.expectStatus().isEqualTo(status).expectBody().jsonPath("$.code").isEqualTo(code);
        services.get("order-ms").verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("로그아웃(blacklist)한 토큰 → 401 TOKEN_REVOKED, 서비스에 도착하지 않음")
    void blacklisted() {
        String token = TestTokenFactory.access(TestTokenFactory.USER_ID, "USER", false, Instant.now());
        tokenState.blacklist(TestTokenFactory.jtiOf(token));

        expectRejected(getOrders(token), 401, "TOKEN_REVOKED");
    }

    @Test
    @DisplayName("무효화(비밀번호 변경 등) 전에 발급된 토큰 → 401, 무효화 후 새로 발급된 토큰 → 통과")
    void revokedAt() {
        Instant now = Instant.now();
        String before = TestTokenFactory.access(TestTokenFactory.USER_ID, "USER", false, now.minusSeconds(60));
        String after = TestTokenFactory.access(TestTokenFactory.USER_ID, "USER", false, now);
        tokenState.revoke(TestTokenFactory.USER_ID, now.minusSeconds(30));

        expectRejected(getOrders(before), 401, "TOKEN_REVOKED");
        getOrders(after).expectStatus().isOk();
    }

    @Test
    @DisplayName("토큰 상태 저장소 장애 → 503 AUTH_UNAVAILABLE (fail-closed)")
    void storeDown() {
        tokenState.failing(true);

        expectRejected(getOrders(TestTokenFactory.access(TestTokenFactory.USER_ID, "USER", false, Instant.now())),
                503, "AUTH_UNAVAILABLE");
    }

    @Test
    @DisplayName("저장소 장애여도 공개 경로(로그인)는 Gateway가 막지 않는다")
    void publicPathIgnoresStore() {
        tokenState.failing(true);

        client.post().uri("/api/v1/auth/login").exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("비밀번호 변경이 필요한 토큰 → 다른 API는 403 PASSWORD_CHANGE_REQUIRED, 비밀번호 변경 API는 통과")
    void passwordChangeRequired() {
        String token = TestTokenFactory.access(TestTokenFactory.USER_ID, "USER", true, Instant.now());

        expectRejected(getOrders(token), 403, "PASSWORD_CHANGE_REQUIRED");
        client.patch().uri("/api/v1/users/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk();
    }
}
