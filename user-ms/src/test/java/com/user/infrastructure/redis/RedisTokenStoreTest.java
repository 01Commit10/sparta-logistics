package com.user.infrastructure.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Redis에 쓰는 키·값·TTL (U05 테스트 9). 키 문자열은 gateway-edge와의 계약이라 문서의 문자열로 비교한다.
 * 실행: sh ./gradlew :user-ms:test -PincludeContainer
 */
@Tag("container")
@Testcontainers
@DataRedisTest
@Import(RedisTokenStore.class)
class RedisTokenStoreTest {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String KEY = "auth:refresh:00000000-0000-0000-0000-000000000001";

    @Autowired
    RedisTokenStore tokenStore;

    @Autowired
    StringRedisTemplate redis;

    private static final String BLACKLIST_KEY = "auth:blacklist:jti-access";

    @AfterEach
    void clean() {
        redis.delete(KEY);
        redis.delete(BLACKLIST_KEY);
    }

    @Test
    @DisplayName("auth:refresh:{userId} 키에 jti를 저장하고 TTL을 건다")
    void savesRefresh() {
        tokenStore.saveRefresh(USER_ID, "jti-1", Duration.ofDays(7));

        assertThat(redis.opsForValue().get(KEY)).isEqualTo("jti-1");
        assertThat(redis.getExpire(KEY)).isBetween(Duration.ofDays(7).toSeconds() - 5, Duration.ofDays(7).toSeconds());
    }

    @Test
    @DisplayName("다시 저장하면 이전 jti를 덮어쓰고 TTL도 새로 건다 (세션 1개)")
    void overwrites() {
        tokenStore.saveRefresh(USER_ID, "jti-1", Duration.ofMinutes(1));

        tokenStore.saveRefresh(USER_ID, "jti-2", Duration.ofDays(7));

        assertThat(redis.opsForValue().get(KEY)).isEqualTo("jti-2");
        assertThat(redis.getExpire(KEY)).isGreaterThan(Duration.ofMinutes(1).toSeconds());
    }

    @Test
    @DisplayName("U08: Refresh 삭제 → auth:refresh:{userId} 키가 없어진다 (없을 때 지워도 오류 없음)")
    void deletesRefresh() {
        tokenStore.saveRefresh(USER_ID, "jti-1", Duration.ofDays(7));

        tokenStore.deleteRefresh(USER_ID);
        tokenStore.deleteRefresh(USER_ID);

        assertThat(redis.hasKey(KEY)).isFalse();
    }

    @Test
    @DisplayName("U08: auth:blacklist:{jti} = logout, TTL = 남은 유효 시간 (Gateway가 읽는 계약)")
    void blacklists() {
        tokenStore.blacklist("jti-access", Duration.ofMinutes(12));

        assertThat(redis.opsForValue().get(BLACKLIST_KEY)).isEqualTo("logout");
        assertThat(redis.getExpire(BLACKLIST_KEY)).isBetween(Duration.ofMinutes(12).toSeconds() - 5, Duration.ofMinutes(12).toSeconds());
    }
}
