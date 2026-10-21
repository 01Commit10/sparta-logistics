package com.company.product.application.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** product.hot.* 기본값과 시작 시 검증 (P03 4장) */
class ProductHotPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(ProductHotProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    @DisplayName("기본값: TOP 10, 후보 30, 5초마다 갱신, 안전 TTL 1분, 초기화 1000개")
    void defaults() {
        runner.run(context -> {
            ProductHotProperties properties = context.getBean(ProductHotProperties.class);
            assertThat(properties.size()).isEqualTo(10);
            assertThat(properties.candidateSize()).isEqualTo(30);
            assertThat(properties.refreshInterval()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.localTtl()).isEqualTo(Duration.ofMinutes(1));
            assertThat(properties.warmupSize()).isEqualTo(1000);
        });
    }

    @Test
    @DisplayName("후보 수가 TOP N보다 적으면 기동 실패")
    void candidateSmallerThanSize() {
        runner.withPropertyValues("product.hot.size=10", "product.hot.candidate-size=5")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasRootCauseMessage("product.hot.candidate-size(5)는 size(10) 이상이어야 합니다."));
    }

    @Test
    @DisplayName("안전 TTL이 갱신 주기 이하면 기동 실패 (정상 갱신 중에도 스냅샷이 비어 버린다)")
    void ttlNotLongerThanRefresh() {
        runner.withPropertyValues("product.hot.refresh-interval=5s", "product.hot.local-ttl=5s")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasRootCauseMessage("product.hot.local-ttl(PT5S)은 refresh-interval(PT5S)보다 길어야 합니다."));
    }
}
