package com.gateway.routing;

import com.gateway.support.WireMockServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * API 명세(docs/design/04 API 명세서)의 외부 경로가 올바른 서비스로 전달되는지 확인한다.
 * 하위 서비스는 WireMock, 서비스 발견은 SimpleDiscoveryClient (Eureka 없음).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RoutingIntegrationTest {

    static final WireMockServices services = WireMockServices.startAll();

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

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
            "POST,  /api/v1/auth/login,                     user-ms",
            "GET,   /api/v1/users/me,                       user-ms",
            "GET,   /api/v1/companies,                      company-ms",
            "POST,  /api/v1/companies/c-1/products,         company-ms",
            "PATCH, /api/v1/products/p-1,                   company-ms",
            "GET,   /api/v1/hubs/h-1,                       hub-ms",
            "GET,   /api/v1/hub-routes,                     hub-ms",
            "GET,   /api/v1/inventories,                    hub-ms",
            "POST,  /api/v1/inventories/i-1/increase,       hub-ms",
            "POST,  /api/v1/orders,                         order-ms",
            "GET,   /api/v1/deliveries/d-1,                 delivery-ms",
            "PATCH, /api/v1/delivery-routes/r-1/status,     delivery-ms",
    })
    @DisplayName("API 명세의 외부 경로가 담당 서비스에 도착한다")
    void routesToOwningService(String method, String path, String service) {
        services.get(service).stubFor(any(urlPathEqualTo(path)).willReturn(aResponse().withStatus(200)));

        client.method(HttpMethod.valueOf(method)).uri(path)
                .exchange()
                .expectStatus().isOk();

        services.get(service).verify(1, anyRequestedFor(urlPathEqualTo(path)));
        WireMockServices.SERVICES.stream()
                .filter(other -> !other.equals(service))
                .forEach(other -> services.get(other).verify(0, anyRequestedFor(anyUrl())));
    }

    @Test
    @DisplayName("메서드·경로·쿼리스트링·본문·헤더를 그대로 전달한다")
    void forwardsRequestAsIs() {
        services.get("order-ms").stubFor(any(anyUrl()).willReturn(aResponse().withStatus(202)));

        client.post().uri("/api/v1/orders?source=web")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "order-key-1")
                .bodyValue("{\"productId\":\"p-1\",\"quantity\":3}")
                .exchange()
                .expectStatus().isAccepted();

        services.get("order-ms").verify(postRequestedFor(urlEqualTo("/api/v1/orders?source=web"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Idempotency-Key", equalTo("order-key-1"))
                .withRequestBody(equalToJson("{\"productId\":\"p-1\",\"quantity\":3}")));
    }

    @Test
    @DisplayName("하위 서비스의 상태 코드·본문·헤더를 그대로 돌려준다")
    void returnsDownstreamResponseAsIs() {
        services.get("hub-ms").stubFor(any(anyUrl()).willReturn(aResponse()
                .withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withHeader("X-Downstream", "hub")
                .withBody("{\"code\":\"INSUFFICIENT_STOCK\",\"message\":\"재고가 부족합니다.\"}")));

        client.post().uri("/api/v1/inventories/i-1/increase")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().valueEquals("X-Downstream", "hub")
                .expectBody().jsonPath("$.code").isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    @DisplayName("어느 라우트에도 맞지 않는 경로는 404이고 하위 서비스로 가지 않는다")
    void unknownPath_isNotFound() {
        client.get().uri("/api/v1/unknown")
                .exchange()
                .expectStatus().isNotFound();

        WireMockServices.SERVICES.forEach(service -> services.get(service).verify(0, anyRequestedFor(anyUrl())));
    }
}
