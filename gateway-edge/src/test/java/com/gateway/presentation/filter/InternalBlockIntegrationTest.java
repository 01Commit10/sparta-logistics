package com.gateway.presentation.filter;

import com.gateway.support.WireMockServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;

/**
 * 누군가 실수로 내부 API 경로를 라우트에 넣어도 Gateway가 외부 요청을 막는다.
 * 이를 확인하려고 테스트에서만 /internal/**, /api/v1/internal/** 를 user-ms로 보내는 라우트를 둔다.
 * (리스트 설정은 합쳐지지 않고 통째로 바뀌므로 이 테스트의 라우트는 이것 하나뿐이다.)
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.gateway.server.webflux.routes[0].id=misconfigured-internal",
                "spring.cloud.gateway.server.webflux.routes[0].uri=lb://user-ms",
                "spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/internal/**,/api/v1/internal/**,/INTERNAL/**"
        })
@AutoConfigureWebTestClient
class InternalBlockIntegrationTest {

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

    @ParameterizedTest
    @ValueSource(strings = {
            "/internal/v1/users/00000000-0000-0000-0000-000000000001",
            "/api/v1/internal/anything",
            "/INTERNAL/v1/users/1",
    })
    @DisplayName("라우트가 있어도 내부 API 경로는 404이고 하위 서비스에 도착하지 않는다")
    void internalPath_isBlocked(String path) {
        services.get("user-ms").stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));

        client.get().uri(path)
                .exchange()
                .expectStatus().isNotFound();

        services.get("user-ms").verify(0, anyRequestedFor(anyUrl()));
    }
}
