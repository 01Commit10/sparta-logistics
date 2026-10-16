package com.common.notification.slack;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Slack 알림 설정 (application-common.yaml의 common.notification.slack).
 *
 * @param webhookUrl     Incoming Webhook URL. 비밀값이라 .env(SLACK_WEBHOOK_URL)에만 둔다(N3). 비어 있으면 알림을 끈다(N13)
 * @param connectTimeout 연결 타임아웃 (N4)
 * @param readTimeout    응답 대기 타임아웃 (N4)
 * @param poolSize       알림 전용 스레드 수 (N11)
 * @param queueCapacity  대기열 크기. 넘치면 버린다 (N11)
 * @param shutdownWait   종료 시 남은 알림을 기다리는 최대 시간 (P10)
 */
@ConfigurationProperties("common.notification.slack")
public record SlackProperties(
        @DefaultValue("") String webhookUrl,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("2s") Duration readTimeout,
        @DefaultValue("2") int poolSize,
        @DefaultValue("100") int queueCapacity,
        @DefaultValue("3s") Duration shutdownWait) {

    public SlackProperties {
        webhookUrl = webhookUrl == null ? "" : webhookUrl.strip();
    }

    public boolean enabled() {
        return !webhookUrl.isEmpty();
    }
}
