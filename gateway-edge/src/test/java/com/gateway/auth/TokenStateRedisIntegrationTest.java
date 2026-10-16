package com.gateway.auth;

import com.gateway.support.TestTokenFactory;
import com.gateway.support.WireMockServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;

/**
 * GW-07 테스트 14: user-ms가 쓰는 형식 그대로 실제 Redis에 키를 넣으면 Gateway가 막는다 (계약 확인).
 * 실행: sh ./gradlew :gateway-edge:test -PincludeContainer
 */
@Tag("container")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "gateway.test.in-memory-token-state=false")
@AutoConfigureWebTestClient
class TokenStateRedisIntegrationTest {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    static final WireMockServices services = WireMockServices.start(List.of("order-ms"));

    @DynamicPropertySource
    static void registerServices(DynamicPropertyRegistry registry) {
        services.registerInstances(registry);
    }

    @Autowired
    WebTestClient client;

    @Autowired
    ReactiveStringRedisTemplate redis;

    @AfterEach
    void reset() {
        services.resetAll();
        redis.getConnectionFactory().getReactiveConnection().serverCommands().flushAll().block();
    }

    @AfterAll
    static void stop() {
        services.stopAll();
    }

    private WebTestClient.ResponseSpec getOrders(String token) {
        services.get("order-ms").stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
        return client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    @Test
    @DisplayName("상태 키가 없으면 통과")
    void passes() {
        getOrders(TestTokenFactory.access(TestTokenFactory.USER_ID, "USER", false, Instant.now())).expectStatus().isOk();
    }

    @Test
    @DisplayName("auth:blacklist:{jti} = logout → 401 TOKEN_REVOKED")
    void blacklisted() {
        String token = TestTokenFactory.access(TestTokenFactory.USER_ID, "USER", false, Instant.now());
        redis.opsForValue().set("auth:blacklist:" + TestTokenFactory.jtiOf(token), "logout").block();

        getOrders(token).expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("TOKEN_REVOKED");
        services.get("order-ms").verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("auth:revoked-at:{userId} = 발급 이후 epoch 초 → 401 TOKEN_REVOKED")
    void revokedAt() {
        Instant issuedAt = Instant.now().minusSeconds(60);
        String token = TestTokenFactory.access(TestTokenFactory.USER_ID, "USER", false, issuedAt);
        redis.opsForValue().set("auth:revoked-at:" + TestTokenFactory.USER_ID,
                String.valueOf(issuedAt.plusSeconds(30).getEpochSecond())).block();

        getOrders(token).expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("TOKEN_REVOKED");
    }
}
