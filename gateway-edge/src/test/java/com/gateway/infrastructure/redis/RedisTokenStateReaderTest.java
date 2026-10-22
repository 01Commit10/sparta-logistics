package com.gateway.infrastructure.redis;

import com.gateway.domain.token.TokenState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 실제 Redis에서 토큰 상태 읽기 (GW-07 테스트 10~11). 운영 설정과 같은 500ms 명령 타임아웃을 쓴다.
 * 실행: sh ./gradlew :gateway-edge:test -PincludeContainer
 */
@Tag("container")
@Testcontainers
class RedisTokenStateReaderTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BLACKLIST = "auth:blacklist:jti-1";
    private static final String REVOKED_AT = "auth:revoked-at:" + USER_ID;

    private final GenericContainer<?> container = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private LettuceConnectionFactory connectionFactory;
    private ReactiveStringRedisTemplate redis;
    private RedisTokenStateReader reader;

    private void start() {
        container.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(container.getHost(), container.getMappedPort(6379)),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new ReactiveStringRedisTemplate(connectionFactory);
        reader = new RedisTokenStateReader(redis);
    }

    @AfterEach
    void stop() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        container.stop();
    }

    @Test
    @DisplayName("10. 키가 없으면 유효한 상태")
    void noKeys() {
        start();

        StepVerifier.create(reader.read(USER_ID, "jti-1"))
                .expectNext(TokenState.active())
                .verifyComplete();
    }

    @Test
    @DisplayName("10. blacklist와 revoked-at(epoch 초)을 함께 읽는다")
    void readsBothKeys() {
        start();
        redis.opsForValue().set(BLACKLIST, "logout").block();
        redis.opsForValue().set(REVOKED_AT, "1792116004").block();

        StepVerifier.create(reader.read(USER_ID, "jti-1"))
                .expectNext(new TokenState(true, Optional.of(Instant.ofEpochSecond(1792116004)), false))
                .verifyComplete();
    }

    @Test
    @DisplayName("10. revoked-at 값이 숫자가 아니면 오염 상태 (fail-closed로 거부됨)")
    void corruptedRevokedAt() {
        start();
        redis.opsForValue().set(REVOKED_AT, "not-a-number").block();

        StepVerifier.create(reader.read(USER_ID, "jti-1"))
                .expectNext(new TokenState(false, Optional.empty(), true))
                .verifyComplete();
    }

    @Test
    @DisplayName("11. Redis가 멈추면 1초 안에 오류 신호를 보낸다 (요청이 오래 묶이지 않게)")
    void redisDown() {
        start();
        StepVerifier.create(reader.read(USER_ID, "jti-1")).expectNextCount(1).verifyComplete();   // 연결을 먼저 맺는다

        container.stop();

        StepVerifier.create(reader.read(USER_ID, "jti-1"))
                .expectError()
                .verify(Duration.ofSeconds(1));
    }
}
