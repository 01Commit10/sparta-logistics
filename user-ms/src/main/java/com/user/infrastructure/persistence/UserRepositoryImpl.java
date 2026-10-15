package com.user.infrastructure.persistence;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.domain.entity.User;
import com.user.domain.repository.UserRepository;
import com.user.domain.vo.UserRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Locale;
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

    /**
     * 바로 flush해서 UNIQUE 위반을 이 자리에서 업무 오류(409)로 바꾼다.
     * 중복 확인(exists)을 동시에 통과한 요청도 500이 아니라 DUPLICATE_*로 응답하게 하기 위해서다.
     */
    @Override
    public User save(User user) {
        try {
            return jpa.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
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

    /**
     * 제약 이름으로 판단하고, 없으면 PostgreSQL 오류 상세("Key (username)=(…) already exists")로 판단한다.
     * ddl-auto update로 만든 기존 DB에는 Hibernate가 자동으로 지은 이름의 username UNIQUE가 남아 있을 수 있다.
     */
    private static RuntimeException translate(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains(User.UK_SLACK_ID_ACTIVE) || message.contains("key (slack_id)")) {
            return new BusinessException(UserErrorCode.DUPLICATE_SLACK_ID);
        }
        if (message.contains(User.UK_USERNAME) || message.contains("key (username)")) {
            return new BusinessException(UserErrorCode.DUPLICATE_USERNAME);
        }
        return e;
    }
}
