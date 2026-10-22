package com.company.product.infrastructure.cache;

/**
 * 상품 조회 캐시 단계 (P01 실험). none: 캐시 없음, redis: 리모트 캐시, layered: Caffeine(L1) + Redis(L2),
 * hot (P03): 인기 상품 TOP N만 L1 스냅샷 + 순위는 Redis Sorted Set, 상세·검색은 Redis + 키 단위 single-flight
 */
public enum ProductCacheMode {
    NONE, REDIS, LAYERED, HOT
}
