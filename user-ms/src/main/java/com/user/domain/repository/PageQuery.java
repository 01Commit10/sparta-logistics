package com.user.domain.repository;

import java.util.List;

/**
 * 목록 조회의 페이지·정렬 요청. domain Port가 Spring Data(Pageable)에 의존하지 않도록 둔다.
 * 정렬 필드 허용 여부는 presentation의 PagePolicy가 먼저 확인한다 (property는 엔티티 필드명).
 */
public record PageQuery(int page, int size, List<Order> orders) {

    public PageQuery {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("page는 0 이상, size는 1 이상이어야 합니다. (page=%d, size=%d)".formatted(page, size));
        }
        orders = List.copyOf(orders);
    }

    public long offset() {
        return (long) page * size;
    }

    public record Order(String property, boolean ascending) {
    }
}
