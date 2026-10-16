package com.gateway.infrastructure.redis;

import com.common.auth.AuthContract;
import com.gateway.domain.token.TokenState;
import com.gateway.domain.token.TokenStateReader;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * user-ms가 쓴 토큰 상태를 읽는다 (docs/plan/02 인증 계약 2.2). 요청당 MGET 한 번으로 키 두 개를 읽는다.
 * 쓰기는 하지 않는다. 연결 실패·타임아웃(spring.data.redis.timeout)은 Mono.error로 그대로 올려 호출 쪽이 503으로 거부한다.
 */
@Component
public class RedisTokenStateReader implements TokenStateReader {

    private final ReactiveStringRedisTemplate redis;

    public RedisTokenStateReader(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    /** [blacklist, revoked-at] 순서 */
    static List<String> keys(UUID userId, String jti) {
        return List.of(AuthContract.REDIS_BLACKLIST_PREFIX + jti, AuthContract.REDIS_REVOKED_AT_PREFIX + userId);
    }

    @Override
    public Mono<TokenState> read(UUID userId, String jti) {
        return redis.opsForValue().multiGet(keys(userId, jti))
                .map(values -> toState(values.get(0), values.get(1)));
    }

    private static TokenState toState(String blacklist, String revokedAt) {
        if (revokedAt == null) {
            return new TokenState(blacklist != null, Optional.empty(), false);
        }
        try {
            return new TokenState(blacklist != null, Optional.of(Instant.ofEpochSecond(Long.parseLong(revokedAt.strip()))), false);
        } catch (NumberFormatException e) {
            return new TokenState(blacklist != null, Optional.empty(), true);
        }
    }
}
