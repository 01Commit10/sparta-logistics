package com.common.page;

import org.springframework.data.domain.Sort;

/**
 * 페이지 응답에 담는 정렬 한 건. Spring의 Sort를 그대로 직렬화하면 내부 구조(empty, sorted 등)가 노출되어 따로 둔다.
 */
public record SortOrder(String property, Sort.Direction direction) {

    public static SortOrder from(Sort.Order order) {
        return new SortOrder(order.getProperty(), order.getDirection());
    }
}
