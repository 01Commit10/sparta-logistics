package com.common.notification.slack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SlackPropertiesTest {

    static final String URL = "https://hooks.slack.com/services/T000/B000/secret-token";

    @Test
    @DisplayName("9-4. toString()에 Webhook URL이 없다 (설정 객체가 로그·예외에 찍혀도 비밀값이 새지 않게, N3)")
    void toStringMasksWebhookUrl() {
        String text = properties(URL).toString();

        assertThat(text).doesNotContain("secret-token").doesNotContain("hooks.slack.com")
                .contains("webhookUrl=****", "connectTimeout=PT1S", "readTimeout=PT2S", "poolSize=2");
    }

    @Test
    @DisplayName("9-4. URL이 비어 있으면 toString()에 미설정으로 나온다")
    void toStringShowsUnset() {
        assertThat(properties("").toString()).contains("webhookUrl=(미설정)");
    }

    @Test
    @DisplayName("9-4. 마스킹은 toString()만 바꾸고 값·equals는 그대로다")
    void keepsValue() {
        assertThat(properties(URL).webhookUrl()).isEqualTo(URL);
        assertThat(properties(URL)).isEqualTo(properties(URL));
    }

    static SlackProperties properties(String webhookUrl) {
        return new SlackProperties(webhookUrl, Duration.ofSeconds(1), Duration.ofSeconds(2), 2, 100, Duration.ofSeconds(3));
    }
}
