package com.user.infrastructure.redis;

import com.common.exception.AuthErrorCode;
import com.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Redis에 연결할 수 없으면 503 AUTH_UNAVAILABLE (U05 테스트 10). 아무도 듣지 않는 포트로 연결해 Docker 없이 확인한다.
 */
class RedisTokenStoreFailureTest {

    private final LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
            new RedisStandaloneConfiguration("localhost", 1),
            LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(1)).build());

    private RedisTokenStore tokenStore() {
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        return new RedisTokenStore(new StringRedisTemplate(connectionFactory));
    }

    @AfterEach
    void close() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("Refresh 저장 중 Redis 연결 실패는 AUTH_UNAVAILABLE")
    void connectionFailure() {
        RedisTokenStore tokenStore = tokenStore();

        assertThatThrownBy(() -> tokenStore.saveRefresh(UUID.randomUUID(), "jti-1", Duration.ofDays(7)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_UNAVAILABLE));
    }

    @Test
    @DisplayName("U08: 로그아웃 중 Redis 연결 실패도 AUTH_UNAVAILABLE")
    void logoutConnectionFailure() {
        RedisTokenStore tokenStore = tokenStore();

        assertThatThrownBy(() -> tokenStore.deleteRefresh(UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_UNAVAILABLE));
        assertThatThrownBy(() -> tokenStore.blacklist("jti-1", Duration.ofMinutes(1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_UNAVAILABLE));
    }

    @Test
    @DisplayName("U07: 재발급 중 Redis 연결 실패도 AUTH_UNAVAILABLE (Refresh가 없다는 401로 바꾸지 않음)")
    void reissueConnectionFailure() {
        RedisTokenStore tokenStore = tokenStore();

        assertThatThrownBy(() -> tokenStore.findRefreshJti(UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_UNAVAILABLE));
    }
}
