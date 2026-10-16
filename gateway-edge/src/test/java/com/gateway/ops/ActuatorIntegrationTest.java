package com.gateway.ops;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static org.hamcrest.Matchers.containsInAnyOrder;

/**
 * GW-11 운영 확인. 운영 정보는 관리 포트에서만 보이고, 서비스 포트에는 헬스체크용 /livez·/readyz만 둔다.
 * Redis는 닿지 않는 포트로 두어 Redis 장애 상황을 만든다 (Docker 없이).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "spring.data.redis.port=1"
})
@AutoConfigureWebTestClient(timeout = "10s")
class ActuatorIntegrationTest {

    @Autowired
    WebTestClient client;

    @LocalManagementPort
    int managementPort;

    private WebTestClient management() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + managementPort)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Test
    @DisplayName("1. 서비스 포트 /livez: 토큰 없이 200 UP, 구성 요소는 보이지 않는다")
    void liveness() {
        client.get().uri("/livez").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components").doesNotExist();
    }

    @Test
    @DisplayName("2. 서비스 포트 /readyz: Redis가 죽어도 200 UP (보호 경로는 GW-07이 503으로 처리)")
    void readinessIgnoresRedis() {
        client.get().uri("/readyz").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components").doesNotExist();
    }

    @Test
    @DisplayName("3. 관리 포트 /actuator/health: Redis 장애가 DOWN으로 보이고 원인 구성 요소를 표시한다")
    void healthShowsRedis() {
        management().get().uri("/actuator/health").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo("DOWN")
                .jsonPath("$.components.redis.status").isEqualTo("DOWN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/health", "/actuator/gateway/routes", "/actuator/circuitbreakers", "/actuator/env"})
    @DisplayName("4. 서비스 포트의 /actuator/**는 공통 형식 404 (운영 정보 비노출)")
    void notOnServicePort(String path) {
        client.get().uri(path).exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("5. 관리 포트 /actuator/gateway/routes: 라우트 5개")
    void routes() {
        management().get().uri("/actuator/gateway/routes").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[*].route_id").value(containsInAnyOrder("user-ms", "company-ms", "hub-ms", "order-ms", "delivery-ms"));
    }

    @Test
    @DisplayName("6. 관리 포트 /actuator/circuitbreakers: 서비스별 회로 5개")
    void circuitBreakers() {
        management().get().uri("/actuator/circuitbreakers").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.circuitBreakers.user-ms").exists()
                .jsonPath("$.circuitBreakers.company-ms").exists()
                .jsonPath("$.circuitBreakers.hub-ms").exists()
                .jsonPath("$.circuitBreakers.order-ms").exists()
                .jsonPath("$.circuitBreakers.delivery-ms").exists();
    }

    @Test
    @DisplayName("7. gateway 엔드포인트는 읽기 전용: 라우트 새로고침(POST refresh)은 막힌다")
    void gatewayReadOnly() {
        management().post().uri("/actuator/gateway/refresh").exchange()
                .expectStatus().value(status -> org.assertj.core.api.Assertions.assertThat(status).isIn(404, 405));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/configprops", "/actuator/beans", "/actuator/heapdump"})
    @DisplayName("8. 관리 포트에서도 설정·메모리 정보는 노출하지 않는다 (비밀값 포함 가능)")
    void sensitiveNotExposed(String path) {
        management().get().uri(path).exchange()
                .expectStatus().isNotFound();
    }
}
