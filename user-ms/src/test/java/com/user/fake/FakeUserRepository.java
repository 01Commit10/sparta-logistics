package com.user.fake;

import com.user.domain.user.User;
import com.user.domain.user.UserRepository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 테스트용 메모리 저장소. 잠금은 흉내 내지 않는다 (잠금은 UserRepositoryPostgresTest에서 확인). */
public class FakeUserRepository implements UserRepository {

    private final Map<UUID, User> store = new LinkedHashMap<>();

    @Override
    public User save(User user) {
        store.put(user.getId(), user);
        return user;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<User> findActiveById(UUID id) {
        return findById(id).filter(User::isActive);
    }

    @Override
    public Optional<User> findActiveByUsername(String username) {
        return store.values().stream().filter(User::isActive).filter(u -> u.getUsername().equals(username)).findFirst();
    }

    @Override
    public boolean existsByUsername(String username) {
        return store.values().stream().anyMatch(u -> u.getUsername().equals(username));
    }

    @Override
    public boolean existsActiveBySlackId(String slackId) {
        return store.values().stream().filter(User::isActive).anyMatch(u -> u.getSlackId().equals(slackId));
    }

    @Override
    public long countActiveMastersForUpdate() {
        return store.values().stream().filter(User::isActive).filter(User::isMaster).count();
    }

    @Override
    public boolean existsActiveMaster() {
        return countActiveMastersForUpdate() > 0;
    }

    public Collection<User> all() {
        return store.values();
    }
}
