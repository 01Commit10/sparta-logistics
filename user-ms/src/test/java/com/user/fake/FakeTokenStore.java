package com.user.fake;

import com.user.domain.token.TokenStore;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 테스트용 메모리 저장소. TTL은 만료시키지 않고 값만 기록한다.
 * 쓰기 순서는 operations()로, 읽기·쓰기 사이에 다른 요청이 끼어드는 상황은 afterNext* hook으로 만든다.
 */
public class FakeTokenStore implements TokenStore {

    public record Saved(String jti, Duration ttl) {
    }

    public record Revoked(Instant at, Duration ttl) {
    }

    private final Map<UUID, Saved> refresh = new HashMap<>();
    private final Map<String, Duration> blacklist = new HashMap<>();
    private final Map<UUID, Revoked> revokedAt_ = new HashMap<>();
    private int deleteCount;
    private final List<String> operations = new ArrayList<>();
    private Runnable afterNextFindRefreshJti;
    private Runnable afterNextSaveRefresh;

    /** 다음 findRefreshJti가 값을 읽은 직후 한 번 실행한다 */
    public void afterNextFindRefreshJti(Runnable action) {
        this.afterNextFindRefreshJti = action;
    }

    /** 다음 saveRefresh가 저장한 직후 한 번 실행한다 */
    public void afterNextSaveRefresh(Runnable action) {
        this.afterNextSaveRefresh = action;
    }

    /** 쓰기 순서 기록: saveRefresh, deleteRefresh, deleteRefreshIfMatches, revokeAllIssuedBefore, blacklist */
    public List<String> operations() {
        return List.copyOf(operations);
    }

    private static void runOnce(Runnable action) {
        if (action != null) {
            action.run();
        }
    }

    @Override
    public void saveRefresh(UUID userId, String jti, Duration ttl) {
        refresh.put(userId, new Saved(jti, ttl));
        operations.add("saveRefresh");
        Runnable action = afterNextSaveRefresh;
        afterNextSaveRefresh = null;
        runOnce(action);
    }

    @Override
    public Optional<String> findRefreshJti(UUID userId) {
        Optional<String> found = refreshOf(userId).map(Saved::jti);
        Runnable action = afterNextFindRefreshJti;
        afterNextFindRefreshJti = null;
        runOnce(action);
        return found;
    }

    @Override
    public void deleteRefresh(UUID userId) {
        refresh.remove(userId);
        deleteCount++;
        operations.add("deleteRefresh");
    }

    @Override
    public void deleteRefreshIfMatches(UUID userId, String jti) {
        refresh.computeIfPresent(userId, (id, saved) -> saved.jti().equals(jti) ? null : saved);
        operations.add("deleteRefreshIfMatches");
    }

    /** 재사용 탐지로 지웠는지 확인한다 (지울 값이 없어도 센다) */
    public int deleteCount() {
        return deleteCount;
    }

    @Override
    public void revokeAllIssuedBefore(UUID userId, Instant revokedAt, Duration ttl) {
        revokedAt_.put(userId, new Revoked(revokedAt, ttl));
        operations.add("revokeAllIssuedBefore");
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
        operations.add("blacklist");
    }

    public Optional<Duration> blacklistTtlOf(String jti) {
        return Optional.ofNullable(blacklist.get(jti));
    }

    public Optional<Saved> refreshOf(UUID userId) {
        return Optional.ofNullable(refresh.get(userId));
    }
}
