package com.common.page;

import java.util.List;

public record PageQuery(int page, int size, List<PageOrder> orderList) {

    public PageQuery {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("page는 0 이상, size는 1 이상이어야 합니다. (page=%d, size=%d)".formatted(page, size));
        }
        orderList = List.copyOf(orderList);
    }

    public long offset() {
        return (long) page * size;
    }

    public record PageOrder(String property, boolean ascending) {
    }

}
