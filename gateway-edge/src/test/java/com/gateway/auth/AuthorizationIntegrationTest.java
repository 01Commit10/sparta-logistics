package com.gateway.auth;

import com.gateway.support.TestTokenFactory;
import com.gateway.support.WireMockServices;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * GW-06: MASTER 전용 경로 인가와 사용자 헤더 주입.
 * 헤더 이름은 문자열로 직접 적는다. 서비스가 읽는 이름(docs/plan/02 인증 계약 2.3)과 같은지 확인하는 계약 테스트다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AuthorizationIntegrationTest {

    static final WireMockServices services = WireMockServices.start(List.of("user-ms", "order-ms", "company-ms"));

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
        List.of("user-ms", "order-ms", "company-ms")
                .forEach(name -> services.get(name).stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200))));
    }

    @Test
    @DisplayName("USER가 MASTER 전용 API를 호출하면 403 ACCESS_DENIED, 서비스에 도착하지 않는다")
    void userOnMasterApi() {
        stubAll();
        String userToken = TestTokenFactory.bearer("USER");

        client.get().uri("/api/v1/users").header(HttpHeaders.AUTHORIZATION, userToken)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("ACCESS_DENIED");
        client.post().uri("/api/v1/companies").header(HttpHeaders.AUTHORIZATION, userToken)
                .exchange()
                .expectStatus().isForbidden();

        services.get("user-ms").verify(0, anyRequestedFor(anyUrl()));
        services.get("company-ms").verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("MASTER는 MASTER 전용 API에 도착한다")
    void masterOnMasterApi() {
        stubAll();

        client.get().uri("/api/v1/users").header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer("MASTER"))
                .exchange()
                .expectStatus().isOk();

        services.get("user-ms").verify(1, getRequestedFor(urlPathEqualTo("/api/v1/users"))
                .withHeader("X-User-Role", equalTo("MASTER")));
    }

    @Test
    @DisplayName("USER의 /users/me는 MASTER 규칙에 걸리지 않고, 토큰의 사용자 헤더가 붙어 도착한다")
    void userOnMe() {
        stubAll();
        UUID userId = UUID.randomUUID();
        Instant issuedAt = Instant.now();
        String token = TestTokenFactory.access(userId, "USER", false, issuedAt);

        client.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        services.get("user-ms").verify(1, getRequestedFor(urlPathEqualTo("/api/v1/users/me"))
                .withHeader("X-User-Id", equalTo(userId.toString()))
                .withHeader("X-User-Role", equalTo("USER"))
                .withHeader("X-Token-Jti", matching("[0-9a-f-]{36}"))
                .withHeader("X-Token-Exp", equalTo(String.valueOf(issuedAt.plus(TestTokenFactory.ACCESS_TTL).getEpochSecond()))));
    }

    @Test
    @DisplayName("업체 수정(MASTER·업체관리자)처럼 데이터를 봐야 아는 권한은 Gateway가 막지 않는다")
    void ownershipCheckedByService() {
        stubAll();

        client.patch().uri("/api/v1/companies/c-1").header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer("USER"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("위조한 X-User-*를 함께 보내도 토큰 값으로 덮인다 (USER 토큰 + X-User-Role: MASTER)")
    void forgedHeadersAreReplacedByToken() {
        stubAll();

        client.get().uri("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer("USER"))
                .header("X-User-Id", "00000000-0000-0000-0000-0000000000ff")
                .header("X-User-Role", "MASTER")
                .exchange()
                .expectStatus().isOk();
        client.get().uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer("USER"))
                .header("X-User-Role", "MASTER")
                .exchange()
                .expectStatus().isForbidden();

        List<LoggedRequest> received = services.get("order-ms").findAll(getRequestedFor(urlPathEqualTo("/api/v1/orders")));
        assertThat(received).hasSize(1);
        assertThat(received.get(0).getHeaders().getHeader("X-User-Id").values())
                .containsExactly(TestTokenFactory.USER_ID.toString());
        assertThat(received.get(0).getHeaders().getHeader("X-User-Role").values()).containsExactly("USER");
    }

    @Test
    @DisplayName("계약: 서비스가 받는 사용자 헤더는 X-User-Id, X-User-Role, X-Token-Jti, X-Token-Exp 4개다")
    void headerContract() {
        stubAll();

        client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer("USER"))
                .exchange()
                .expectStatus().isOk();

        LoggedRequest received = services.get("order-ms").findAll(getRequestedFor(anyUrl())).get(0);
        Pattern trusted = Pattern.compile("(?i)x-(user|token)-.*");
        List<String> trustedHeaders = received.getHeaders().keys().stream()
                .filter(name -> trusted.matcher(name).matches())
                .toList();
        assertThat(trustedHeaders).containsExactlyInAnyOrder("X-User-Id", "X-User-Role", "X-Token-Jti", "X-Token-Exp");
        assertThat(received.getHeader("X-Token-Exp")).matches("\\d{10}");
    }
}
