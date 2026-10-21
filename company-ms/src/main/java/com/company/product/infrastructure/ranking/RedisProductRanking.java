package com.company.product.infrastructure.ranking;

import com.company.product.domain.service.ProductRanking;
import com.company.product.domain.service.RankedProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 인기 순위를 Redis Sorted Set 하나에 둔다 (P03). 모든 서버가 같은 순위를 본다.
 * 점수는 RedisProductViewCounter의 Lua가 조회수를 셀 때 누적값으로 덮어쓴다(ZADD). TTL 없음.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "product.cache.mode", havingValue = "hot")
public class RedisProductRanking implements ProductRanking {

    public static final String KEY = "company-ms::product-rank";

    private final StringRedisTemplate redis;

    @Override
    public List<RankedProduct> top(int count) {
        Set<TypedTuple<String>> tuples = redis.opsForZSet().reverseRangeWithScores(KEY, 0, count - 1L);
        if (tuples == null) {
            return List.of();
        }
        return tuples.stream()
                .map(tuple -> new RankedProduct(UUID.fromString(tuple.getValue()), tuple.getScore().longValue()))
                .toList();
    }

    @Override
    public void remove(UUID productId) {
        redis.opsForZSet().remove(KEY, productId.toString());
    }

    // ZCARD로 비었는지 보고 ZADD NX로 넣는다. 그 사이 조회수가 먼저 들어와도 NX라 덮지 않는다
    @Override
    public void warmUpIfEmpty(List<RankedProduct> products) {
        if (products.isEmpty()) {
            return;
        }
        Long size = redis.opsForZSet().zCard(KEY);
        if (size != null && size > 0) {
            return;
        }
        Set<TypedTuple<String>> tuples = products.stream()
                .map(product -> TypedTuple.of(product.productId().toString(), (double) product.viewCount()))
                .collect(Collectors.toSet());
        redis.opsForZSet().addIfAbsent(KEY, tuples);
    }
}
