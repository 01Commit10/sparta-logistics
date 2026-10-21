package com.company.fake;

import com.company.product.application.cache.HotProductCache;

import java.time.Duration;

/** 테스트용 인기 상품 스냅샷. hot 모드가 아닐 때처럼 아무도 채우지 않으면 늘 비어 있다 */
public final class HotProductCaches {

    private HotProductCaches() {
    }

    public static HotProductCache empty() {
        return new HotProductCache(10, Duration.ofMinutes(1), System::nanoTime);
    }
}
