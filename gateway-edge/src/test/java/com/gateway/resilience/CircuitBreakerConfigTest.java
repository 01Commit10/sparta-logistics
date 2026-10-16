package com.gateway.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 application.yaml의 Circuit Breaker 설정 (GW-10, SYS-13 4장).
 * <ul>
 *     <li>라우트마다 CB 하나, 이름 = route id (서비스별로 따로 열린다)</li>
 *     <li>fallbackUri 없음: 열림은 CallNotPermittedException → 503, 타임아웃 504·연결 실패 503은 GW-04 매핑 그대로</li>
 *     <li>statusCodes 없음: 연결 실패·타임아웃만 실패로 센다. 하위 4xx·5xx 응답은 본문 그대로 전달한다</li>
 *     <li>TimeLimiter 끔: 타임아웃은 GW-09 라우트 응답 타임아웃 하나만 쓴다 (기본 1초 TimeLimiter가 먼저 끊는 문제 방지)</li>
 *     <li>Retry 필터 없음: POST 재전송으로 주문이 중복 생성되지 않게 한다 (SYS-13 6장)</li>
 * </ul>
 */
class CircuitBreakerConfigTest {

    private static Binder binder() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application.yaml", new ClassPathResource("application.yaml"))
                .forEach(environment.getPropertySources()::addLast);
        return Binder.get(environment);
    }

    private static List<RouteDefinition> routes() throws IOException {
        return binder().bind("spring.cloud.gateway.server.webflux.routes", Bindable.listOf(RouteDefinition.class)).get();
    }

    @Test
    @DisplayName("2. 라우트 5개 모두 CircuitBreaker 필터 1개, name = route id, fallbackUri·statusCodes 없음")
    void everyRouteHasOwnCircuitBreaker() throws IOException {
        assertThat(routes()).hasSize(5).allSatisfy(route -> {
            List<FilterDefinition> breakers = route.getFilters().stream()
                    .filter(filter -> filter.getName().equals("CircuitBreaker")).toList();
            assertThat(breakers).hasSize(1);
            assertThat(breakers.get(0).getArgs())
                    .containsEntry("name", route.getId())
                    .doesNotContainKeys("fallbackUri")
                    .allSatisfy((key, value) -> assertThat(key).doesNotStartWith("statusCodes"));
        });
    }

    @Test
    @DisplayName("Retry 필터가 없다")
    void noRetry() throws IOException {
        assertThat(routes()).flatExtracting(RouteDefinition::getFilters)
                .extracting(FilterDefinition::getName)
                .doesNotContain("Retry");
        assertThat(binder().bind("spring.cloud.gateway.server.webflux.default-filters", Bindable.listOf(FilterDefinition.class)))
                .matches(bound -> !bound.isBound() || bound.get().stream().noneMatch(f -> f.getName().equals("Retry")));
    }

    @Test
    @DisplayName("열림 조건: 최근 10건 중 5건 이상 & 실패율 50%, 10초 뒤 시험 3건. TimeLimiter는 끈다")
    void breakerSettings() throws IOException {
        Binder binder = binder();
        String prefix = "resilience4j.circuitbreaker.configs.default.";

        assertThat(binder.bind(prefix + "sliding-window-type", String.class).get()).isEqualTo("COUNT_BASED");
        assertThat(binder.bind(prefix + "sliding-window-size", Integer.class).get()).isEqualTo(10);
        assertThat(binder.bind(prefix + "minimum-number-of-calls", Integer.class).get()).isEqualTo(5);
        assertThat(binder.bind(prefix + "failure-rate-threshold", Float.class).get()).isEqualTo(50f);
        assertThat(binder.bind(prefix + "wait-duration-in-open-state", java.time.Duration.class).get()).hasSeconds(10);
        assertThat(binder.bind(prefix + "permitted-number-of-calls-in-half-open-state", Integer.class).get()).isEqualTo(3);
        assertThat(binder.bind("spring.cloud.circuitbreaker.resilience4j.disable-time-limiter", Boolean.class).get()).isTrue();
    }
}
