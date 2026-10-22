package com.company.product.application.cache;

import com.company.product.application.dto.ProductResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** L1 인기 상품 스냅샷 (P03): 통째로 덮어써서 비는 순간이 없고, 안전 TTL이 지나면 옛 값을 주지 않는다 */
class HotProductCacheTest {

    private final AtomicLong now = new AtomicLong();
    private final HotProductCache cache = new HotProductCache(10, Duration.ofMinutes(1), now::get);

    private static List<ProductResult> products(String prefix, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new ProductResult(UUID.randomUUID(), UUID.randomUUID(), prefix + i, null,
                        new BigDecimal("1000"), (long) (count - i), LocalDateTime.now(), LocalDateTime.now()))
                .toList();
    }

    @Test
    @DisplayName("replace 전에는 비어 있고, replace 후 top은 넣은 순서 그대로, find는 hit")
    void replaceAndRead() {
        assertThat(cache.top(10)).isEmpty();

        List<ProductResult> snapshot = products("상품", 10);
        cache.replace(snapshot);

        assertThat(cache.top(10)).containsExactlyElementsOf(snapshot);
        assertThat(cache.top(3)).containsExactlyElementsOf(snapshot.subList(0, 3));
        assertThat(cache.find(snapshot.get(4).id())).contains(snapshot.get(4));
        assertThat(cache.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("replace 중에 읽어도 옛 스냅샷 전체 또는 새 스냅샷 전체만 보인다 (섞이지 않고 비지 않음)")
    void replaceIsAtomic() throws Exception {
        List<ProductResult> oldOnes = products("옛", 10);
        List<ProductResult> newOnes = products("새", 10);
        cache.replace(oldOnes);
        AtomicBoolean running = new AtomicBoolean(true);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<Boolean>> readers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            readers.add(pool.submit(() -> {
                while (running.get()) {
                    List<ProductResult> seen = cache.top(10);
                    if (!seen.equals(oldOnes) && !seen.equals(newOnes)) {
                        return false;
                    }
                }
                return true;
            }));
        }

        for (int i = 0; i < 20_000; i++) {
            cache.replace(i % 2 == 0 ? newOnes : oldOnes);
        }
        running.set(false);

        for (Future<Boolean> reader : readers) {
            assertThat(reader.get(5, TimeUnit.SECONDS)).isTrue();
        }
        pool.shutdown();
    }

    @Test
    @DisplayName("invalidate한 상품은 find miss, top에서도 빠진다 (나머지 순서는 그대로)")
    void invalidate() {
        List<ProductResult> snapshot = products("상품", 3);
        cache.replace(snapshot);

        cache.invalidate(snapshot.get(1).id());

        assertThat(cache.find(snapshot.get(1).id())).isEmpty();
        assertThat(cache.top(10)).containsExactly(snapshot.get(0), snapshot.get(2));
    }

    @Test
    @DisplayName("안전 TTL이 지나면 빈 스냅샷 (스케줄러가 멈춰도 옛 순위를 계속 주지 않는다)")
    void expiresAfterLocalTtl() {
        List<ProductResult> snapshot = products("상품", 3);
        cache.replace(snapshot);

        now.addAndGet(Duration.ofSeconds(59).toNanos());
        assertThat(cache.top(10)).hasSize(3);

        now.addAndGet(Duration.ofSeconds(2).toNanos());
        assertThat(cache.top(10)).isEmpty();
        assertThat(cache.find(snapshot.get(0).id())).isEmpty();
    }

    @Test
    @DisplayName("invalidate는 TTL 시작 시각을 늘리지 않는다")
    void invalidateKeepsAge() {
        List<ProductResult> snapshot = products("상품", 3);
        cache.replace(snapshot);
        now.addAndGet(Duration.ofSeconds(59).toNanos());

        cache.invalidate(snapshot.get(0).id());
        now.addAndGet(Duration.ofSeconds(2).toNanos());

        assertThat(cache.top(10)).isEmpty();
    }

    @Test
    @DisplayName("담는 최대 개수는 size: 더 많이 넣어도 앞에서부터 size개만")
    void keepsAtMostSize() {
        cache.replace(products("상품", 15));

        assertThat(cache.top(20)).hasSize(10);
        assertThat(cache.maxSize()).isEqualTo(10);
    }
}
