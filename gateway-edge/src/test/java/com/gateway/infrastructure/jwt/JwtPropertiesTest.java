package com.gateway.infrastructure.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    @DisplayName("Base64로 32바이트 이상인 키는 바인딩된다")
    void validKey() {
        runner.withPropertyValues("jwt.secret-key=" + keyOfBytes(32))
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtProperties.class));
    }

    @Test
    @DisplayName("키가 없으면 기동에 실패한다")
    void missingKey() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("빈 키는 기동에 실패한다 (.env에 JWT_SECRET_KEY= 만 있는 경우)")
    void blankKey() {
        runner.withPropertyValues("jwt.secret-key=").run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("31바이트 키는 HS256에 짧아서 기동에 실패한다")
    void shortKey() {
        runner.withPropertyValues("jwt.secret-key=" + keyOfBytes(31))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Base64가 아닌 키는 기동에 실패한다")
    void notBase64() {
        runner.withPropertyValues("jwt.secret-key=not base64 !!!@@@###$$$%%%^^^&&&***((()))")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("키 값은 toString에 드러나지 않는다 (로그 노출 방지)")
    void toStringHidesKey() {
        String key = keyOfBytes(32);

        assertThat(new JwtProperties(key).toString()).doesNotContain(key);
    }

    private static String keyOfBytes(int length) {
        return Base64.getEncoder().encodeToString(new byte[length]).replace("A", "Q");
    }
}
