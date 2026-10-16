package com.user.fake;

import com.user.domain.token.TokenStore;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 테스트용 메모리 저장소. TTL은 만료시키지 않고 값만 기록한다. */
public class FakeTokenStore implements TokenStore {

    public record Saved(String jti, Duration ttl) {
    }

    private final Map<UUID, Saved> refresh = new HashMap<>();
    private final Map<String, Duration> blacklist = new HashMap<>();

    @Override
    public void saveRefresh(UUID userId, String jti, Duration ttl) {
        refresh.put(userId, new Saved(jti, ttl));
    }

    @Override
    public void deleteRefresh(UUID userId) {
        refresh.remove(userId);
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        blacklist.put(jti, ttl);
    }

    public Optional<Duration> blacklistTtlOf(String jti) {
        return Optional.ofNullable(blacklist.get(jti));
    }

    public Optional<Saved> refreshOf(UUID userId) {
        return Optional.ofNullable(refresh.get(userId));
    }
}
