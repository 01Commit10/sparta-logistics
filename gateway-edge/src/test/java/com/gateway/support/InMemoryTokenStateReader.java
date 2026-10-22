package com.gateway.support;

import com.gateway.domain.token.TokenState;
import com.gateway.domain.token.TokenStateReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 통합 테스트용 토큰 상태 저장소 (Redis 없이, docs/plan/01 구현 원칙 3.2). 테스트 클래스패스에만 있고 컴포넌트 스캔으로 등록된다.
 * 실제 Redis를 쓰는 테스트(@Tag("container"))는 {@code gateway.test.in-memory-token-state=false}로 끈다.
 */
@Primary
@Component
@ConditionalOnProperty(name = "gateway.test.in-memory-token-state", havingValue = "true", matchIfMissing = true)
public class InMemoryTokenStateReader implements TokenStateReader {

    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Instant> revokedAt = new ConcurrentHashMap<>();
    private volatile boolean failing;

    @Override
    public Mono<TokenState> read(UUID userId, String jti) {
        if (failing) {
            return Mono.error(new IllegalStateException("token state store unavailable (test)"));
        }
        return Mono.just(new TokenState(blacklist.contains(jti), Optional.ofNullable(revokedAt.get(userId)), false));
    }

    public void blacklist(String jti) {
        blacklist.add(jti);
    }

    public void revoke(UUID userId, Instant at) {
        revokedAt.put(userId, at);
    }

    public void failing(boolean failing) {
        this.failing = failing;
    }

    public void clear() {
        blacklist.clear();
        revokedAt.clear();
        failing = false;
    }
}
