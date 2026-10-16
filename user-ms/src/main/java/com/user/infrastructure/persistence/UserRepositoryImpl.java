package com.user.infrastructure.persistence;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.domain.entity.User;
import com.user.domain.repository.PageData;
import com.user.domain.repository.PageQuery;
import com.user.domain.repository.UserRepository;
import com.user.domain.repository.UserSearchCondition;
import com.user.domain.vo.UserRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * UserRepository Port의 JPA 구현. application은 이 클래스를 모른다 (DIP).
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    /** 목록 정렬에 쓸 수 있는 엔티티 필드. 허용 여부는 presentation PagePolicy가 먼저 400으로 거르고, 여기서 한 번 더 막는다 */
    private static final Set<String> SORTABLE = Set.of("createdAt", "username", "name", "id");

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
    public boolean existsActiveBySlackIdAndIdNot(String slackId, UUID id) {
        return jpa.existsBySlackIdAndIdNotAndDeletedAtIsNull(slackId, id);
    }

    @Override
    public long countActiveMastersForUpdate() {
        return jpa.findActiveByRoleForUpdate(UserRole.MASTER).size();
    }

    @Override
    public boolean existsActiveMaster() {
        return jpa.existsByRoleAndDeletedAtIsNull(UserRole.MASTER);
    }

    @Override
    public PageData<User> search(UserSearchCondition condition, PageQuery pageQuery) {
        Page<User> page = jpa.findAll(UserSpecifications.of(condition),
                PageRequest.of(pageQuery.page(), pageQuery.size(), toSort(pageQuery.orders())));
        return new PageData<>(page.getContent(), page.getTotalElements());
    }

    private static Sort toSort(List<PageQuery.Order> orders) {
        return Sort.by(orders.stream()
                .map(order -> {
                    if (!SORTABLE.contains(order.property())) {
                        throw new IllegalArgumentException("정렬할 수 없는 필드입니다: " + order.property());
                    }
                    return order.ascending() ? Sort.Order.asc(order.property()) : Sort.Order.desc(order.property());
                })
                .toList());
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
