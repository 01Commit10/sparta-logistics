package com.user.fake;

import com.user.domain.entity.User;
import com.user.domain.repository.PageData;
import com.user.domain.repository.PageQuery;
import com.user.domain.repository.UserRepository;
import com.user.domain.repository.UserSearchCondition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 테스트용 메모리 저장소. 잠금은 흉내 내지 않는다 (잠금은 UserRepositoryPostgresTest에서 확인).
 * search는 조건 필터와 페이지 자르기만 하고 정렬은 저장 순서 그대로다 (정렬은 UserRepositorySearchTest에서 실제 DB로 확인).
 */
public class FakeUserRepository implements UserRepository {

    private final Map<UUID, User> store = new LinkedHashMap<>();
    private int lockCount;
    private UserSearchCondition lastCondition;
    private PageQuery lastPageQuery;

    /** 잠금 조회(countActiveMastersForUpdate) 횟수 */
    public int lockCount() {
        return lockCount;
    }

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
    public boolean existsActiveBySlackIdAndIdNot(String slackId, UUID id) {
        return store.values().stream().filter(User::isActive).filter(u -> !u.getId().equals(id))
                .anyMatch(u -> u.getSlackId().equals(slackId));
    }

    @Override
    public long countActiveMastersForUpdate() {
        lockCount++;
        return store.values().stream().filter(User::isActive).filter(User::isMaster).count();
    }

    @Override
    public boolean existsActiveMaster() {
        return store.values().stream().filter(User::isActive).anyMatch(User::isMaster);
    }

    @Override
    public PageData<User> search(UserSearchCondition condition, PageQuery pageQuery) {
        lastCondition = condition;
        lastPageQuery = pageQuery;
        List<User> matched = store.values().stream()
                .filter(u -> condition.includeDeleted() || u.isActive())
                .filter(u -> condition.username() == null || u.getUsername().contains(condition.username()))
                .filter(u -> condition.name() == null || u.getName().contains(condition.name()))
                .filter(u -> condition.role() == null || u.getRole() == condition.role())
                .filter(u -> condition.deliveryManager() == null || u.isDeliveryManager() == condition.deliveryManager())
                .filter(u -> condition.organizationType() == null || u.getOrganizationType() == condition.organizationType())
                .toList();
        List<User> page = matched.stream().skip(pageQuery.offset()).limit(pageQuery.size()).toList();
        return new PageData<>(page, matched.size());
    }

    public UserSearchCondition lastCondition() {
        return lastCondition;
    }

    public PageQuery lastPageQuery() {
        return lastPageQuery;
    }

    public Collection<User> all() {
        return store.values();
    }
}
