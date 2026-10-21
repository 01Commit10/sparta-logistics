package com.company.product.application.service;

import com.common.page.PageQuery;
import com.common.page.PageQuery.PageOrder;
import com.company.product.application.dto.ProductSearchQuery;

/**
 * 인기 상품 TOP N 요청인지 판별 (P03 D1). 기존 URL(GET /products?sort=viewCount,desc&size≤N)을 그대로 두고
 * 검색 조건이 없는 첫 페이지만 L1 인기 상품 스냅샷으로 보낸다.
 */
final class PopularProductMatcher {

    private PopularProductMatcher() {
    }

    static boolean matches(ProductSearchQuery search, PageQuery page, int maxSize) {
        if (search.companyId() != null || (search.name() != null && !search.name().isBlank())) {
            return false;
        }
        if (page.page() != 0 || page.size() > maxSize || page.orderList().isEmpty()) {
            return false;
        }
        PageOrder first = page.orderList().get(0);
        return first.property().equals("viewCount") && !first.ascending();
    }
}
