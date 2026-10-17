package com.common.page;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 목록 조회 Pageable 정규화 (07 서비스 정책 1.5). Controller에서 상수로 두고 Service 호출 전에 적용.
 * - size: 1~100만 허용, 그 외 값은 기본값 10으로 변경 (400을 내지 않음)
 * - sort: 허용한 필드만 받는다. 없는 필드로 정렬하면 PropertyReferenceException(500)이 나고,
 *   인덱스 없는 컬럼 정렬도 막기 위함. 허용되지 않은 필드면 400 INVALID_SORT_PROPERTY
 * - 정렬이 없으면 생성일 역순(createdAt DESC). createdAt은 BaseEntity 필드라 항상 허용
 * - 마지막에 id를 보조 정렬로 붙인다. createdAt이 같은 행끼리 순서가 고정되지 않으면
 *   페이지를 넘길 때 데이터가 중복되거나 빠질 수 있기 때문
 * - page는 0부터 시작. 음수 page는 Spring 리졸버가 이미 0으로 바꿔 준다
 *
 * <pre>
 * private static final PagePolicy PAGE_POLICY = PagePolicy.of("name");
 *
 * public PageResponse&lt;CompanyResponse&gt; search(..., Pageable pageable) {
 *     Pageable normalized = PAGE_POLICY.apply(pageable);
 *     ...
 * }
 * </pre>
 */
public final class PagePolicy {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;

    private static final String CREATED_AT = "createdAt";
    private static final String TIE_BREAKER = "id";
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, CREATED_AT);

    private final Set<String> sortableProperties;

    private PagePolicy(Set<String> sortableProperties) {
        this.sortableProperties = sortableProperties;
    }

    /** createdAt 외에 정렬을 허용할 엔티티 필드명 */
    public static PagePolicy of(String... sortableProperties) {
        Set<String> properties = new LinkedHashSet<>();
        properties.add(CREATED_AT);
        properties.addAll(List.of(sortableProperties));
        return new PagePolicy(Collections.unmodifiableSet(properties));
    }

    public Pageable apply(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_SIZE, withTieBreaker(DEFAULT_SORT));
        }
        return PageRequest.of(pageable.getPageNumber(), normalizeSize(pageable.getPageSize()),
                withTieBreaker(normalizeSort(pageable.getSort())));
    }

    private static int normalizeSize(int size) {
        return size >= 1 && size <= MAX_SIZE ? size : DEFAULT_SIZE;
    }

    private Sort normalizeSort(Sort sort) {
        if (sort.isUnsorted()) {
            return DEFAULT_SORT;
        }
        for (Sort.Order order : sort) {
            if (!sortableProperties.contains(order.getProperty())) {
                throw new BusinessException(CommonErrorCode.INVALID_SORT_PROPERTY,
                        "정렬할 수 없는 필드입니다. (요청: %s, 허용: %s)"
                                .formatted(order.getProperty(), sortableProperties));
            }
        }
        return sort;
    }

    private static Sort withTieBreaker(Sort sort) {
        if (sort.getOrderFor(TIE_BREAKER) != null) {
            return sort;
        }
        List<Sort.Order> orders = new ArrayList<>(sort.toList());
        orders.add(Sort.Order.asc(TIE_BREAKER));
        return Sort.by(orders);
    }
}
