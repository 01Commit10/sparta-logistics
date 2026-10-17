package com.user.infrastructure.persistence;

import com.user.domain.entity.User;
import com.user.domain.repository.UserSearchCondition;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * USR-11 목록 검색 조건 → JPA Specification. 조건이 null이면 걸지 않고, 걸린 조건은 모두 AND.
 */
final class UserSpecifications {

    private static final char ESCAPE = '\\';

    private UserSpecifications() {
    }

    static Specification<User> of(UserSearchCondition condition) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!condition.includeDeleted()) {
                predicates.add(cb.isNull(root.get("deletedAt")));
            }
            if (condition.username() != null) {
                predicates.add(cb.like(cb.lower(root.get("username")), containsPattern(condition.username()), ESCAPE));
            }
            if (condition.name() != null) {
                predicates.add(cb.like(cb.lower(root.get("name")), containsPattern(condition.name()), ESCAPE));
            }
            if (condition.role() != null) {
                predicates.add(cb.equal(root.get("role"), condition.role()));
            }
            if (condition.deliveryManager() != null) {
                predicates.add(cb.equal(root.get("deliveryManager"), condition.deliveryManager()));
            }
            if (condition.organizationType() != null) {
                predicates.add(cb.equal(root.get("organizationType"), condition.organizationType()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** 부분 일치 (대소문자 무시). 검색어의 %·_는 와일드카드가 아니라 글자로 찾는다 */
    private static String containsPattern(String keyword) {
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
