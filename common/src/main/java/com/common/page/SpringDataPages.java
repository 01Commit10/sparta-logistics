package com.common.page;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class SpringDataPages {

    private SpringDataPages() {
    }

    public static Pageable toPageable(PageQuery query) {
        return PageRequest.of(query.page(), query.size(),
                Sort.by(query.orderList().stream().map(pageOrder -> pageOrder.ascending() ?
                                Sort.Order.asc(pageOrder.property()) :
                                Sort.Order.desc(pageOrder.property()))
                        .toList()));
    }

    public static <T> PageData<T> toPageData(Page<T> page) {
        return new PageData<>(page.getContent(),
                page.getTotalElements());
    }
}
