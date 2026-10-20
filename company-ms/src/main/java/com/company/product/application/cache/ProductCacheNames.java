package com.company.product.application.cache;

/** 상품 조회 캐시 이름. TTL은 application.yaml product.cache.ttl.{이름}에서 정한다 */
public final class ProductCacheNames {

    // 상품 상세 (사용자 상세 조회). 수정·삭제 시 지운다
    public static final String DETAIL = "product-detail";
    // 상품 목록·검색·인기 상품 (조건 + 페이지). 지우지 않고 짧은 TTL로만 갱신한다
    public static final String SEARCH = "product-search";

    private ProductCacheNames() {
    }
}
