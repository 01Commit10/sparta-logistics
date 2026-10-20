package com.company.product.infrastructure.cache;

import com.common.config.CommonRedisAutoConfiguration;
import com.common.page.PageData;
import com.company.product.application.dto.ProductResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Redis(L2)에 담는 값이 common의 JSON 직렬화로 그대로 되살아나는지 */
class ProductCacheSerializationTest {

    @SuppressWarnings("unchecked")
    private final SerializationPair<Object> pair = (SerializationPair<Object>) new CommonRedisAutoConfiguration()
            .redisCacheConfiguration("company-ms").getValueSerializationPair();

    private final ProductResult product = new ProductResult(UUID.randomUUID(), UUID.randomUUID(), "고등어 1kg",
            "냉동", new BigDecimal("5000.00"), 42L, LocalDateTime.of(2026, 10, 19, 12, 0, 1), null);

    private Object roundTrip(Object value) {
        ByteBuffer bytes = pair.write(value);
        return pair.read(bytes);
    }

    @Test
    @DisplayName("상품 상세(ProductResult): 단가·날짜·UUID 그대로")
    void detail() {
        assertThat(roundTrip(product)).isEqualTo(product);
    }

    @Test
    @DisplayName("상품 목록(PageData<ProductResult>): 내용·전체 건수 그대로")
    @SuppressWarnings("unchecked")
    void page() {
        PageData<ProductResult> page = new PageData<>(List.of(product), 1_000_000);

        PageData<ProductResult> restored = (PageData<ProductResult>) roundTrip(page);

        assertThat(restored.content()).containsExactly(product);
        assertThat(restored.totalElements()).isEqualTo(1_000_000);
    }
}
