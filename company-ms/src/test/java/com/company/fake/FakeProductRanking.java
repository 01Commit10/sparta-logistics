package com.company.fake;

import com.company.product.domain.service.ProductRanking;
import com.company.product.domain.service.RankedProduct;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 테스트용 메모리 순위. 점수 내림차순. 실제 Sorted Set 동작은 RedisProductRankingTest(container)에서 확인 */
public class FakeProductRanking implements ProductRanking {

    private final Map<UUID, Long> scores = new LinkedHashMap<>();
    private final List<UUID> removed = new ArrayList<>();
    private Runnable onTop = () -> {
    };
    private RuntimeException failure;

    public void score(UUID productId, long viewCount) {
        scores.put(productId, viewCount);
    }

    /** top이 불릴 때 먼저 실행할 일 (갱신 도중 상태 확인용) */
    public void onTop(Runnable hook) {
        this.onTop = hook;
    }

    /** 다음 top 호출부터 이 예외를 던진다 (Redis 장애 흉내) */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public List<UUID> removed() {
        return removed;
    }

    @Override
    public List<RankedProduct> top(int count) {
        onTop.run();
        if (failure != null) {
            throw failure;
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(count)
                .map(entry -> new RankedProduct(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public void remove(UUID productId) {
        scores.remove(productId);
        removed.add(productId);
    }

    @Override
    public void warmUpIfEmpty(List<RankedProduct> products) {
        if (scores.isEmpty()) {
            products.forEach(product -> scores.putIfAbsent(product.productId(), product.viewCount()));
        }
    }
}
