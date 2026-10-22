package com.company.product.infrastructure.viewcount;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Redis에 모은 조회수를 주기적으로 DB에 반영 (product.view-count.flush-interval, 기본 5초) */
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnExpression("'${product.cache.mode:none}' != 'none'")
public class ProductViewCountFlusher {

    private static final int BATCH_SIZE = 1000;

    private final RedisProductViewCounter counter;

    @Scheduled(fixedDelayString = "${product.view-count.flush-interval:5s}")
    public void flush() {
        while (counter.flush(BATCH_SIZE) == BATCH_SIZE) {
            // 한 번에 다 못 옮겼으면 남은 것도 바로 반영
        }
    }
}
