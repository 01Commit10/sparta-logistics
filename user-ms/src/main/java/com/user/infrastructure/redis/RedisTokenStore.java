package com.user.infrastructure.redis;

import com.common.auth.AuthContract;
import com.common.exception.AuthErrorCode;
import com.common.exception.BusinessException;
import com.user.domain.token.TokenStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 토큰 상태를 Redis에 기록한다. 키는 common AuthContract의 접두사를 쓴다 (docs/plan/02 인증 계약 2.2).
 * Redis 오류(연결 실패·시간 초과·인증 실패)는 토큰 상태를 남길 수 없다는 뜻이라 503 AUTH_UNAVAILABLE로 바꾼다.
 * 로그에는 예외 종류만 남긴다 (jti·키 값 없음).
 */
@Slf4j
@Component
public class RedisTokenStore implements TokenStore {

    private final StringRedisTemplate redis;

    public RedisTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 로그아웃한 Access Token의 값. Gateway는 키가 있는지만 본다 (02 인증 계약 2.2) */
    private static final String BLACKLIST_VALUE = "logout";

    @Override
    public void saveRefresh(UUID userId, String jti, Duration ttl) {
        write("Refresh Token 저장", () -> redis.opsForValue().set(AuthContract.REDIS_REFRESH_PREFIX + userId, jti, ttl));
    }

    @Override
    public void deleteRefresh(UUID userId) {
        write("Refresh Token 삭제", () -> redis.delete(AuthContract.REDIS_REFRESH_PREFIX + userId));
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        write("Access Token blacklist", () -> redis.opsForValue().set(AuthContract.REDIS_BLACKLIST_PREFIX + jti, BLACKLIST_VALUE, ttl));
    }

    private void write(String action, Runnable command) {
        try {
            command.run();
        } catch (DataAccessException e) {
            log.warn("{}: Redis에 기록하지 못했습니다: {}", action, e.getClass().getSimpleName());
            throw new BusinessException(AuthErrorCode.AUTH_UNAVAILABLE);
        }
    }
}
