package com.gateway.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 application.yaml의 타임아웃 값 (GW-09 3장, SYS-13 3.2). test 프로필은 값을 줄여 쓰므로 기본 프로필만 읽는다.
 * 원칙: 바깥(Gateway) 타임아웃 > 안쪽 호출 시간 합. 주문은 Hub·Delivery를 차례로 호출하므로 따로 길게 둔다.
 * 하위 서비스 보호는 타임아웃만 쓴다. Circuit Breaker·Retry 필터는 두지 않는다 (C03, POST 재전송으로 주문이 중복 생성되지 않게).
 */
class TimeoutPropertiesTest {

    private static final String PREFIX = "spring.cloud.gateway.server.webflux";

    private static Binder binder() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application.yaml", new ClassPathResource("application.yaml"))
                .forEach(environment.getPropertySources()::addLast);
        return Binder.get(environment);
    }

    private static Map<String, RouteDefinition> routes() throws IOException {
        return binder().bind(PREFIX + ".routes", Bindable.listOf(RouteDefinition.class)).get().stream()
                .collect(Collectors.toMap(RouteDefinition::getId, Function.identity()));
    }

    @Test
    @DisplayName("1. 전역 연결 2초, 응답 5초")
    void global() throws IOException {
        HttpClientProperties httpClient = binder().bind(PREFIX + ".httpclient", HttpClientProperties.class).get();

        assertThat(httpClient.getConnectTimeout()).isEqualTo(2000);
        assertThat(httpClient.getResponseTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("1. 주문 라우트만 응답 15초 (metadata는 밀리초 숫자)")
    void orderRoute() throws IOException {
        Map<String, RouteDefinition> routes = routes();

        assertThat(Long.parseLong(routes.get("order-ms").getMetadata().get("response-timeout").toString())).isEqualTo(15000);
        assertThat(List.of("user-ms", "company-ms", "hub-ms", "delivery-ms"))
                .allSatisfy(id -> assertThat(routes.get(id).getMetadata()).doesNotContainKey("response-timeout"));
    }

    @Test
    @DisplayName("C03. 라우트·기본 필터에 CircuitBreaker·Retry 필터가 없다")
    void noCircuitBreakerOrRetryFilter() throws IOException {
        Binder binder = binder();

        assertThat(routes().values()).flatExtracting(RouteDefinition::getFilters)
                .extracting(FilterDefinition::getName)
                .doesNotContain("CircuitBreaker", "Retry");
        assertThat(binder.bind(PREFIX + ".default-filters", Bindable.listOf(FilterDefinition.class)))
                .matches(bound -> !bound.isBound()
                        || bound.get().stream().noneMatch(f -> List.of("CircuitBreaker", "Retry").contains(f.getName())));
    }

    @Test
    @DisplayName("C03. resilience4j 설정·의존성이 없다")
    void noResilience4j() throws IOException {
        Binder binder = binder();

        assertThat(binder.bind("resilience4j", Bindable.mapOf(String.class, Object.class)).isBound()).isFalse();
        assertThat(binder.bind("spring.cloud.circuitbreaker", Bindable.mapOf(String.class, Object.class)).isBound()).isFalse();
        assertThat(ClassUtils.isPresent("io.github.resilience4j.circuitbreaker.CircuitBreaker", getClass().getClassLoader())).isFalse();
    }
}
