package com.common.config;

import com.common.notification.SlackMessage;
import com.common.notification.SlackNotifier;
import com.common.notification.slack.NoOpSlackNotifier;
import com.common.notification.slack.SlackProperties;
import com.common.notification.slack.SlackWebhookNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class CommonNotificationAutoConfigurationTest {

    static final String URL_PROPERTY = "common.notification.slack.webhook-url";
    static final String VALID_URL = "https://hooks.slack.com/services/T000/B000/secret-token";

    final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonNotificationAutoConfiguration.class));

    @Test
    @DisplayName("7-1. URL이 없으면 NoOp을 등록하고 INFO를 한 번 남긴다")
    void noOpWithoutUrl(CapturedOutput output) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).getBean(SlackNotifier.class).isInstanceOf(NoOpSlackNotifier.class);
        });

        assertThat(output.getAll().lines().filter(line -> line.contains("Slack 알림 비활성"))).hasSize(1);
        assertThat(output.getAll().lines().filter(line -> line.contains("Slack 알림 비활성")).findFirst().orElseThrow())
                .contains("INFO");
    }

    @Test
    @DisplayName("7-1. URL이 공백뿐이어도 NoOp")
    void noOpWithBlankUrl() {
        runner.withPropertyValues(URL_PROPERTY + "=   ").run(context ->
                assertThat(context).getBean(SlackNotifier.class).isInstanceOf(NoOpSlackNotifier.class));
    }

    @Test
    @DisplayName("7-2. URL이 있으면 Webhook 발송기를 등록하고, URL 값은 로그에 남기지 않는다")
    void webhookWithUrl(CapturedOutput output) {
        runner.withPropertyValues(URL_PROPERTY + "=" + VALID_URL).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).getBean(SlackNotifier.class).isInstanceOf(SlackWebhookNotifier.class);
        });

        assertThat(output.getAll()).doesNotContain("secret-token");
    }

    @Test
    @DisplayName("7-2. 형식이 틀린 URL이면 기동이 실패하고, 실패 메시지에 URL 값이 없다 (P3)")
    void failsWithInvalidUrl(CapturedOutput output) {
        runner.withPropertyValues(URL_PROPERTY + "=ftp://hooks.slack.com/services/T000/B000/secret-token")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining(URL_PROPERTY)
                            .hasMessageNotContaining("secret-token");
                });

        assertThat(output.getAll()).doesNotContain("secret-token");
    }

    @Test
    @DisplayName("7-3. 서비스가 SlackNotifier 빈을 직접 등록하면 그것을 쓴다")
    void backsOffForCustomBean() {
        runner.withUserConfiguration(CustomNotifierConfig.class)
                .withPropertyValues(URL_PROPERTY + "=" + VALID_URL)
                .run(context -> {
                    assertThat(context).hasSingleBean(SlackNotifier.class);
                    assertThat(context).getBean(SlackNotifier.class).isSameAs(context.getBean("customNotifier"));
                });
    }

    @Test
    @DisplayName("7-4. WebFlux(Gateway)에서는 등록하지 않는다")
    void notInReactive() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonNotificationAutoConfiguration.class))
                .withPropertyValues(URL_PROPERTY + "=" + VALID_URL)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SlackNotifier.class);
                });
    }

    @Test
    @DisplayName("7-4. 웹이 아닌 환경에서도 등록하지 않는다")
    void notInNonWeb() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonNotificationAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(SlackNotifier.class));
    }

    @Test
    @DisplayName("7-5. Executor 빈을 만들지 않아 Boot 기본 applicationTaskExecutor가 그대로 등록된다 (P1)")
    void keepsBootTaskExecutor() {
        runner.withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
                .withPropertyValues(URL_PROPERTY + "=" + VALID_URL)
                .run(context -> {
                    assertThat(context).getBean(SlackNotifier.class).isInstanceOf(SlackWebhookNotifier.class);
                    assertThat(context).hasSingleBean(Executor.class);
                    assertThat(context).hasBean("applicationTaskExecutor");
                });
    }

    @Test
    @DisplayName("7-6. 기본값은 connect 1s·read 2s·스레드 2·대기열 100·종료 대기 3s, 프로퍼티로 바꿀 수 있다")
    void bindsProperties() {
        runner.run(context -> assertThat(context.getBean(SlackProperties.class))
                .isEqualTo(new SlackProperties("", Duration.ofSeconds(1), Duration.ofSeconds(2), 2, 100, Duration.ofSeconds(3))));

        runner.withPropertyValues(
                        "common.notification.slack.connect-timeout=500ms",
                        "common.notification.slack.read-timeout=5s",
                        "common.notification.slack.pool-size=4",
                        "common.notification.slack.queue-capacity=10",
                        "common.notification.slack.shutdown-wait=1s")
                .run(context -> assertThat(context.getBean(SlackProperties.class))
                        .isEqualTo(new SlackProperties("", Duration.ofMillis(500), Duration.ofSeconds(5), 4, 10, Duration.ofSeconds(1))));
    }

    @Test
    @DisplayName("7-7. application-common.yaml이 SLACK_WEBHOOK_URL·SLACK_*_TIMEOUT 환경 값을 연결한다")
    void commonYamlWiring() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "spring.profiles.active=common",
                        "SLACK_WEBHOOK_URL=" + VALID_URL,
                        "SLACK_READ_TIMEOUT=5s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).getBean(SlackNotifier.class).isInstanceOf(SlackWebhookNotifier.class);
                    SlackProperties properties = context.getBean(SlackProperties.class);
                    assertThat(properties.webhookUrl()).isEqualTo(VALID_URL);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(5));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomNotifierConfig {

        @Bean
        SlackNotifier customNotifier() {
            return new SlackNotifier() {
                @Override
                public void send(SlackMessage message) {
                }

                @Override
                public void send(String reference, Supplier<SlackMessage> message) {
                }
            };
        }
    }
}
