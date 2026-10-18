package com.company.product.infrastructure.persistence;

import com.company.product.domain.entity.Product;
import com.company.product.domain.repository.ProductSearchCondition;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 상품 목록 검색 조건 → JPA Specification. 조건이 null이면 걸지 않고, 걸린 조건은 모두 AND.
 * 삭제된 상품과 삭제된 업체의 상품은 항상 제외한다 (업체 조건은 inner join).
 */
final class ProductSpecifications {

    private static final char ESCAPE = '\\';

    private ProductSpecifications() {
    }

    static Specification<Product> of(ProductSearchCondition condition) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.isNull(root.get("company").get("deletedAt")));
            if (condition.companyId() != null) {
                predicates.add(cb.equal(root.get("company").get("id"), condition.companyId()));
            }
            if (condition.name() != null) {
                predicates.add(cb.like(cb.lower(root.get("name")), containsPattern(condition.name()), ESCAPE));
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
