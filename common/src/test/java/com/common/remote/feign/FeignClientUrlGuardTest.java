package com.common.remote.feign;

import com.common.config.CommonRemoteAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C02 테스트 20~23: 고정 url이 있는 @FeignClient는 기동 실패 (Feign은 우리 서비스끼리만, 외부 API는 RestClient).
 */
class FeignClientUrlGuardTest {

    private static final String SECRET_WEBHOOK = "https://hooks.slack.com/services/T000/B000/secret-token";

    @FeignClient(name = "hub-ms")
    interface HubClient {
        @GetMapping("/internal/v1/hubs")
        String hubs();
    }

    @FeignClient(name = "slack", url = SECRET_WEBHOOK)
    interface SlackClient {
        @GetMapping("/")
        String post();
    }

    @FeignClient(name = "company-ms", url = "${test.company.url:}")
    interface PlaceholderClient {
        @GetMapping("/")
        String get();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = HubClient.class)
    static class NameOnly {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = {HubClient.class, SlackClient.class})
    static class WithExternalUrl {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = PlaceholderClient.class)
    static class WithPlaceholderUrl {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class, CommonRemoteAutoConfiguration.class));

    @Test
    @DisplayName("name만 있는 클라이언트(Eureka 이름으로 호출) → 기동 성공")
    void nameOnly() {
        runner.withUserConfiguration(NameOnly.class).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("외부 url이 있으면 기동 실패. 메시지에 클라이언트 이름과 RestClient 안내가 있고, url 값(비밀값일 수 있음)은 없다")
    void externalUrlFails() {
        runner.withUserConfiguration(WithExternalUrl.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("'slack'")
                    .hasMessageContaining(SlackClient.class.getName())
                    .hasMessageContaining("RestClient")
                    .hasMessageNotContaining("secret-token")
                    .hasMessageNotContaining("hooks.slack.com");
        });
    }

    @Test
    @DisplayName("url 플레이스홀더가 빈 값으로 풀리면 → 기동 성공")
    void blankPlaceholder() {
        runner.withUserConfiguration(WithPlaceholderUrl.class).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("url 플레이스홀더가 외부 주소로 풀리면 → 기동 실패")
    void resolvedPlaceholder() {
        runner.withUserConfiguration(WithPlaceholderUrl.class)
                .withPropertyValues("test.company.url=http://company.example.com")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("설정 파일의 client.config.{이름}.url도 고정 url이므로 → 기동 실패")
    void configUrlFails() {
        runner.withUserConfiguration(NameOnly.class)
                .withPropertyValues("spring.cloud.openfeign.client.config.hub-ms.url=http://localhost:19093")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("spring.cloud.openfeign.client.config.hub-ms.url")
                            .hasMessageNotContaining("19093");
                });
    }

    @Test
    @DisplayName("client.config의 다른 값(타임아웃)은 문제없다")
    void configTimeoutIsFine() {
        runner.withUserConfiguration(NameOnly.class)
                .withPropertyValues("spring.cloud.openfeign.client.config.hub-ms.read-timeout=5000")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("lazy-attributes-resolution 모드에서도 외부 url을 잡는다")
    void lazyMode() {
        runner.withUserConfiguration(WithExternalUrl.class)
                .withPropertyValues("spring.cloud.openfeign.lazy-attributes-resolution=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("common.feign.allow-direct-url=true(테스트 전용)면 url이 있어도 기동 성공")
    void allowDirectUrl() {
        runner.withUserConfiguration(WithExternalUrl.class)
                .withPropertyValues("common.feign.allow-direct-url=true")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
