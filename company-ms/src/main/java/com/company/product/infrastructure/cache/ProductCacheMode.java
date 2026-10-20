package com.company.product.infrastructure.cache;

/** 상품 조회 캐시 단계 (P01 실험). none: 캐시 없음, redis: 리모트 캐시, layered: Caffeine(L1) + Redis(L2) */
public enum ProductCacheMode {
    NONE, REDIS, LAYERED
}
