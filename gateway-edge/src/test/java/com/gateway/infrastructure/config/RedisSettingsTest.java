package com.gateway.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 application.yaml의 Redis 연결 값 (GW-07 4장). 장애 시 보호 요청이 이 시간만큼 늦어지므로 1초 미만이어야 한다.
 */
class RedisSettingsTest {

    private static RedisProperties bind() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application.yaml", new ClassPathResource("application.yaml"))
                .forEach(environment.getPropertySources()::addLast);
        return Binder.get(environment).bind("spring.data.redis", RedisProperties.class).get();
    }

    @Test
    @DisplayName("명령·연결 타임아웃 기본값 500ms (Redis 장애 시 1초 안에 503)")
    void timeouts() throws IOException {
        RedisProperties redis = bind();

        assertThat(redis.getTimeout()).isEqualTo(Duration.ofMillis(500));
        assertThat(redis.getConnectTimeout()).isEqualTo(Duration.ofMillis(500));
    }
}
