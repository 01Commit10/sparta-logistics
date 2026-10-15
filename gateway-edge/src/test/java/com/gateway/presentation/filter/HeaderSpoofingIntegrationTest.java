package com.gateway.presentation.filter;

import com.gateway.support.WireMockServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * 외부 사용자가 X-User-Role: MASTER 같은 헤더를 직접 넣어도 하위 서비스에 전달되지 않는다.
 * 토큰이 필요 없는 공개 경로에서도 마찬가지다 (SYS-01 4.2, 5.2).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class HeaderSpoofingIntegrationTest {

    static final WireMockServices services = WireMockServices.start(List.of("user-ms"));

    @DynamicPropertySource
    static void registerServices(DynamicPropertyRegistry registry) {
        services.registerInstances(registry);
    }

    @Autowired
    WebTestClient client;

    @AfterAll
    static void stop() {
        services.stopAll();
    }

    @Test
    @DisplayName("공개 경로로 보낸 위조 사용자 헤더는 하위 서비스에 도착하지 않는다")
    void forgedHeaders_areRemoved() {
        services.get("user-ms").stubFor(post(urlPathEqualTo("/api/v1/auth/login")).willReturn(aResponse().withStatus(200)));

        client.post().uri("/api/v1/auth/login")
                .header("X-User-Id", "00000000-0000-0000-0000-000000000001")
                .header("X-User-Role", "MASTER")
                .header("X-Token-Jti", "forged-jti")
                .header("Idempotency-Key", "k-1")
                .exchange()
                .expectStatus().isOk();

        services.get("user-ms").verify(postRequestedFor(urlPathEqualTo("/api/v1/auth/login"))
                .withHeader("X-User-Id", absent())
                .withHeader("X-User-Role", absent())
                .withHeader("X-Token-Jti", absent())
                .withHeader("Idempotency-Key", equalTo("k-1")));
    }
}
