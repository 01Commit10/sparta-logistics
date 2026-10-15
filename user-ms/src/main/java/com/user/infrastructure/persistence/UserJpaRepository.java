package com.user.infrastructure.persistence;

import com.user.domain.user.User;
import com.user.domain.user.UserRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface UserJpaRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    boolean existsByUsername(String username);

    boolean existsBySlackIdAndDeletedAtIsNull(String slackId);

    boolean existsByRoleAndDeletedAtIsNull(UserRole role);

    /**
     * PostgreSQL은 count(*)에 FOR UPDATE를 쓸 수 없으므로 행을 잠가 조회한 뒤 개수를 센다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role and u.deletedAt is null")
    List<User> findActiveByRoleForUpdate(UserRole role);
}
