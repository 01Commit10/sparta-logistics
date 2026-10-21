package com.company.product.application.service;

import com.common.page.PageQuery;
import com.common.page.PageQuery.PageOrder;
import com.company.product.application.dto.ProductSearchQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 인기 상품 요청 판별 (P03 D1): 조건 없음 · page 0 · size ≤ N · 첫 정렬 viewCount desc 일 때만 L1 스냅샷으로 보낸다 */
class PopularProductMatcherTest {

    private static final int MAX = 10;
    private static final ProductSearchQuery NO_CONDITION = new ProductSearchQuery(null, null);
    private static final List<PageOrder> VIEW_DESC = List.of(new PageOrder("viewCount", false), new PageOrder("id", true));

    private static PageQuery page(int page, int size, List<PageOrder> orders) {
        return new PageQuery(page, size, orders);
    }

    @Test
    @DisplayName("조건 없음 · page 0 · size 10 · viewCount desc → 인기 상품 요청")
    void matches() {
        assertThat(PopularProductMatcher.matches(NO_CONDITION, page(0, 10, VIEW_DESC), MAX)).isTrue();
        assertThat(PopularProductMatcher.matches(NO_CONDITION, page(0, 5, VIEW_DESC), MAX)).isTrue();
        assertThat(PopularProductMatcher.matches(new ProductSearchQuery(null, "  "), page(0, 10, VIEW_DESC), MAX))
                .isTrue();
    }

    @Test
    @DisplayName("검색 조건이 있으면 아니다 (name · companyId)")
    void withCondition() {
        assertThat(PopularProductMatcher.matches(new ProductSearchQuery(null, "생수"), page(0, 10, VIEW_DESC), MAX))
                .isFalse();
        assertThat(PopularProductMatcher.matches(new ProductSearchQuery(UUID.randomUUID(), null),
                page(0, 10, VIEW_DESC), MAX)).isFalse();
    }

    @Test
    @DisplayName("첫 페이지가 아니거나 size가 N보다 크면 아니다")
    void pageOutOfRange() {
        assertThat(PopularProductMatcher.matches(NO_CONDITION, page(1, 10, VIEW_DESC), MAX)).isFalse();
        assertThat(PopularProductMatcher.matches(NO_CONDITION, page(0, 11, VIEW_DESC), MAX)).isFalse();
    }

    @Test
    @DisplayName("정렬이 없거나, viewCount asc거나, 첫 정렬이 viewCount가 아니면 아니다")
    void otherSort() {
        assertThat(PopularProductMatcher.matches(NO_CONDITION, page(0, 10, List.of()), MAX)).isFalse();
        assertThat(PopularProductMatcher.matches(NO_CONDITION,
                page(0, 10, List.of(new PageOrder("viewCount", true))), MAX)).isFalse();
        assertThat(PopularProductMatcher.matches(NO_CONDITION,
                page(0, 10, List.of(new PageOrder("name", true), new PageOrder("viewCount", false))), MAX)).isFalse();
    }
}
