package com.user.infrastructure.persistence;

import com.user.domain.user.User;
import com.user.domain.user.UserRepository;
import com.user.domain.user.UserRole;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * UserRepository Port의 JPA 구현. application은 이 클래스를 모른다 (DIP).
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpa;

    public UserRepositoryImpl(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public User save(User user) {
        return jpa.save(user);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<User> findActiveById(UUID id) {
        return jpa.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<User> findActiveByUsername(String username) {
        return jpa.findByUsernameAndDeletedAtIsNull(username);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpa.existsByUsername(username);
    }

    @Override
    public boolean existsActiveBySlackId(String slackId) {
        return jpa.existsBySlackIdAndDeletedAtIsNull(slackId);
    }

    @Override
    public long countActiveMastersForUpdate() {
        return jpa.findActiveByRoleForUpdate(UserRole.MASTER).size();
    }

    @Override
    public boolean existsActiveMaster() {
        return jpa.existsByRoleAndDeletedAtIsNull(UserRole.MASTER);
    }
}
