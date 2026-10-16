package com.user.fake;

import com.user.domain.token.TokenStore;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 테스트용 메모리 저장소. TTL은 만료시키지 않고 값만 기록한다. */
public class FakeTokenStore implements TokenStore {

    public record Saved(String jti, Duration ttl) {
    }

    public record Revoked(Instant at, Duration ttl) {
    }

    private final Map<UUID, Saved> refresh = new HashMap<>();
    private final Map<String, Duration> blacklist = new HashMap<>();
    private final Map<UUID, Revoked> revokedAt_ = new HashMap<>();
    private int deleteCount;

    @Override
    public void saveRefresh(UUID userId, String jti, Duration ttl) {
        refresh.put(userId, new Saved(jti, ttl));
    }

    @Override
    public Optional<String> findRefreshJti(UUID userId) {
        return refreshOf(userId).map(Saved::jti);
    }

    @Override
    public void deleteRefresh(UUID userId) {
        refresh.remove(userId);
        deleteCount++;
    }

    /** 재사용 탐지로 지웠는지 확인한다 (지울 값이 없어도 센다) */
    public int deleteCount() {
        return deleteCount;
    }

    @Override
    public void revokeAllIssuedBefore(UUID userId, Instant revokedAt, Duration ttl) {
        revokedAt_.put(userId, new Revoked(revokedAt, ttl));
    }

    @Override
    public Optional<Instant> findRevokedAt(UUID userId) {
        return revokedOf(userId).map(Revoked::at);
    }

    public Optional<Revoked> revokedOf(UUID userId) {
        return Optional.ofNullable(revokedAt_.get(userId));
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
