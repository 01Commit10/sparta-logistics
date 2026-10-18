package com.common.remote.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application-common.yaml의 Feign 값이 그대로 적용되는지 (값을 덮어쓰지 않고 확인, SYS-13 3장).
 * 값을 바꾸면 SYS-13·C02 문서와 Gateway 타임아웃(GW-09, 바깥 > 안쪽 합)도 함께 확인한다.
 */
@SpringBootTest(classes = RemoteTestApplication.class)
@ActiveProfiles("common")
class CommonFeignSettingsTest {

    @Autowired
    FeignClientProperties feignProperties;

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
    @DisplayName("Circuit Breaker를 쓰지 않는다: Feign CB 스위치·resilience4j 설정 없음 (C03)")
    void noCircuitBreaker() {
        assertThat(environment.getProperty("spring.cloud.openfeign.circuitbreaker.enabled", Boolean.class, false)).isFalse();
        assertThat(environment.getProperty("resilience4j.circuitbreaker.configs.default.sliding-window-size")).isNull();
        assertThat(ClassUtils.isPresent("io.github.resilience4j.circuitbreaker.CircuitBreaker", getClass().getClassLoader())).isFalse();
    }
}
