package com.gateway.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 라우트는 서비스 단위로 하나씩, id = 서비스 이름, uri = lb://{서비스} (GW-02 3장).
 */
@SpringBootTest
class RouteDefinitionTest {

    @Autowired
    GatewayProperties gatewayProperties;

    @Test
    @DisplayName("라우트는 서비스 5개이고 id가 서비스 이름과 같다")
    void routes_areOnePerService() {
        assertThat(gatewayProperties.getRoutes())
                .extracting(RouteDefinition::getId)
                .containsExactlyInAnyOrder("user-ms", "company-ms", "hub-ms", "order-ms", "delivery-ms");
    }

    @Test
    @DisplayName("모든 라우트는 Eureka 로드밸런싱(lb://{id})으로 전달한다")
    void routes_useLoadBalancer() {
        assertThat(gatewayProperties.getRoutes())
                .allSatisfy(route -> assertThat(route.getUri()).isEqualTo(URI.create("lb://" + route.getId())));
    }

    @Test
    @DisplayName("내부 API(/internal/**) 경로를 가진 라우트는 없다")
    void noRoute_forInternalApi() {
        assertThat(gatewayProperties.getRoutes())
                .flatExtracting(RouteDefinition::getPredicates)
                .flatExtracting(predicate -> predicate.getArgs().values())
                .noneMatch(pattern -> pattern.contains("/internal"));
    }
}
