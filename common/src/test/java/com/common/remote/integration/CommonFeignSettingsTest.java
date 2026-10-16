package com.common.remote.integration;

import com.common.remote.RemoteBusinessException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application-common.yaml의 Feign·Circuit Breaker 값이 그대로 적용되는지 (값을 덮어쓰지 않고 확인, SYS-13 3·4장).
 * 값을 바꾸면 SYS-13·C02 문서와 Gateway 타임아웃(GW-09, 바깥 > 안쪽 합)도 함께 확인한다.
 */
@SpringBootTest(classes = RemoteTestApplication.class)
@ActiveProfiles("common")
class CommonFeignSettingsTest {

    @Autowired
    FeignClientProperties feignProperties;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    @Autowired
    Environment environment;

    @Test
    @DisplayName("Feign 기본 타임아웃: 연결 1초, 응답 2초")
    void feignTimeouts() {
        FeignClientProperties.FeignClientConfiguration defaults = feignProperties.getConfig().get("default");

        assertThat(defaults.getConnectTimeout()).isEqualTo(1000);
        assertThat(defaults.getReadTimeout()).isEqualTo(2000);
    }

    @Test
    @DisplayName("Circuit Breaker: 최근 10회 중 5회 이상 호출, 실패 50%면 OPEN, 10초 뒤 HALF_OPEN, 시험 호출 3회, 4xx 무시")
    void circuitBreakerDefaults() {
        CircuitBreakerConfig config = circuitBreakers.getDefaultConfig();

        assertThat(config.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(Duration.ofSeconds(10).toMillis());
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isTrue();
        assertThat(config.getIgnoreExceptionPredicate().test(
                new RemoteBusinessException("hub-ms", 409, com.common.exception.CommonErrorCode.INVALID_INPUT, "x"))).isTrue();
    }

    @Test
    @DisplayName("Feign Circuit Breaker를 켜고, 호출한 스레드에서 실행한다 (TimeLimiter 없음)")
    void circuitBreakerSwitches() {
        assertThat(environment.getProperty("spring.cloud.openfeign.circuitbreaker.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("spring.cloud.circuitbreaker.resilience4j.disable-thread-pool", Boolean.class)).isTrue();
    }
}
