package com.common.config;

import com.common.notification.SlackNotifier;
import com.common.notification.slack.NoOpSlackNotifier;
import com.common.notification.slack.SlackProperties;
import com.common.notification.slack.SlackWebhookNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Slack 알림 (NTF). Servlet 서비스에만 켜진다(Gateway 제외). 설정값은 application-common.yaml의 common.notification.slack.
 * Webhook URL이 없으면 NoOp(N13), 형식이 틀리면 기동 실패(P3). 서비스가 SlackNotifier 빈을 직접 등록하면 물러난다.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.client.RestClient")
@EnableConfigurationProperties(SlackProperties.class)
public class CommonNotificationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SlackNotifier slackNotifier(SlackProperties properties) {
        if (!properties.enabled()) {
            log.info("Slack 알림 비활성 (common.notification.slack.webhook-url 미설정)");
            return new NoOpSlackNotifier();
        }
        return new SlackWebhookNotifier(properties);
    }
}
