package com.company.product.infrastructure.viewcount;

import com.company.product.domain.entity.Product;
import com.company.product.domain.repository.ProductRepository;
import com.company.product.domain.service.ProductViewCounter;
import com.company.product.infrastructure.ranking.RedisProductRanking;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 조회수를 Redis에 모아 두고(INCR) 주기적으로 DB에 반영한다 (캐시 단계, P01 D5).
 * 키에는 증가분이 아니라 누적값을 둬서 응답에 정확한 조회수를 담는다. 처음 세는 상품은 DB 값에서 시작한다.
 * Redis가 반영 전에 사라지면 마지막 반영 이후 조회수는 잃는다 (정확성보다 처리량을 택한 트레이드오프).
 * 같은 Lua에서 인기 순위(RedisProductRanking) 점수도 누적값으로 덮어써서 응답 조회수와 순위 점수가 항상 같다 (P03).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${product.cache.mode:none}' != 'none'")
public class RedisProductViewCounter implements ProductViewCounter {

    static final String KEY_PREFIX = "company-ms::product-views::";
    static final String DIRTY_KEY = "company-ms::product-views:dirty";

    // 키가 있으면 +1 하고 반영 대상에 넣고 순위 점수를 덮는다 (Redis 왕복 1번). 키가 없으면 nil
    private static final RedisScript<Long> INCREASE_IF_PRESENT = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return nil end
            local count = redis.call('INCR', KEYS[1])
            redis.call('SADD', KEYS[2], ARGV[1])
            redis.call('ZADD', KEYS[3], count, ARGV[1])
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ProductRepository productRepository;

    @Override
    public long increase(UUID productId) {
        List<String> keys = List.of(KEY_PREFIX + productId, DIRTY_KEY, RedisProductRanking.KEY);
        Long count = redis.execute(INCREASE_IF_PRESENT, keys, productId.toString());
        if (count != null) {
            return count;
        }
        // 처음 세는 상품: DB 값에서 시작한다. 동시에 처음 세도 시작값은 한 번만 들어간다(SETNX)
        long base = productRepository.findById(productId).map(Product::getViewCount).orElse(0L);
        redis.opsForValue().setIfAbsent(keys.get(0), String.valueOf(base));
        return redis.execute(INCREASE_IF_PRESENT, keys, productId.toString());
    }

    /** 바뀐 상품의 누적 조회수를 최대 max개 DB에 반영하고, 반영한 개수를 돌려준다 */
    @Transactional
    public int flush(int max) {
        List<String> ids = redis.opsForSet().pop(DIRTY_KEY, max);
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        for (String id : ids) {
            String value = redis.opsForValue().get(KEY_PREFIX + id);
            if (value != null) {
                productRepository.applyViewCount(UUID.fromString(id), Long.parseLong(value));
            }
        }
        return ids.size();
    }
}
