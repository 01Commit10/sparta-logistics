package com.user.infrastructure.redis;

import com.common.auth.AuthContract;
import com.common.exception.AuthErrorCode;
import com.common.exception.BusinessException;
import com.user.domain.token.TokenStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 토큰 상태를 Redis에 기록하고 읽는다. 키는 common AuthContract의 접두사를 쓴다 (docs/plan/02 인증 계약 2.2).
 * Redis 오류(연결 실패·시간 초과·인증 실패)는 토큰 상태를 남기거나 확인할 수 없다는 뜻이라 503 AUTH_UNAVAILABLE로 바꾼다.
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

    private static final RedisScript<Long> DELETE_IF_MATCHES = RedisScript.of(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end", Long.class);

    @Override
    public void saveRefresh(UUID userId, String jti, Duration ttl) {
        write("Refresh Token 저장", () -> redis.opsForValue().set(AuthContract.REDIS_REFRESH_PREFIX + userId, jti, ttl));
    }

    @Override
    public Optional<String> findRefreshJti(UUID userId) {
        return Optional.ofNullable(call("Refresh Token 조회",
                () -> redis.opsForValue().get(AuthContract.REDIS_REFRESH_PREFIX + userId)));
    }

    @Override
    public void deleteRefresh(UUID userId) {
        write("Refresh Token 삭제", () -> redis.delete(AuthContract.REDIS_REFRESH_PREFIX + userId));
    }

    /** 읽고 비교하고 지우는 것을 Redis 안에서 한 번에 한다 (그 사이에 다른 로그인이 저장한 jti를 지우지 않게) */
    @Override
    public void deleteRefreshIfMatches(UUID userId, String jti) {
        write("Refresh Token 조건부 삭제", () -> redis.execute(DELETE_IF_MATCHES,
                List.of(AuthContract.REDIS_REFRESH_PREFIX + userId), jti));
    }

    @Override
    public void revokeAllIssuedBefore(UUID userId, Instant revokedAt, Duration ttl) {
        write("revoked-at 기록", () -> redis.opsForValue().set(AuthContract.REDIS_REVOKED_AT_PREFIX + userId,
                String.valueOf(revokedAt.getEpochSecond()), ttl));
    }

    /** 숫자가 아니면 empty. 그 값은 Gateway가 깨진 상태로 보고 거부하므로 여기서 발급을 막지 않는다 */
    @Override
    public Optional<Instant> findRevokedAt(UUID userId) {
        String value = call("revoked-at 조회", () -> redis.opsForValue().get(AuthContract.REDIS_REVOKED_AT_PREFIX + userId));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.ofEpochSecond(Long.parseLong(value.strip())));
        } catch (NumberFormatException e) {
            log.warn("revoked-at 값이 epoch 초가 아닙니다: userId={}", userId);
            return Optional.empty();
        }
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        write("Access Token blacklist", () -> redis.opsForValue().set(AuthContract.REDIS_BLACKLIST_PREFIX + jti, BLACKLIST_VALUE, ttl));
    }

    private void write(String action, Runnable command) {
        call(action, () -> {
            command.run();
            return null;
        });
    }

    private <T> T call(String action, Supplier<T> command) {
        try {
            return command.get();
        } catch (DataAccessException e) {
            log.warn("{}: Redis를 사용하지 못했습니다: {}", action, e.getClass().getSimpleName());
            throw new BusinessException(AuthErrorCode.AUTH_UNAVAILABLE);
        }
    }
}
