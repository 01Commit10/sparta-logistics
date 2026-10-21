package com.company.product.infrastructure.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 키 단위 single-flight (P03 P3): 같은 키 동시 miss는 원본을 한 번만 읽고, 다른 키는 서로 기다리지 않는다 */
class SingleFlightCacheTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(60);
    private final SingleFlightCache cache = new SingleFlightCache(new ConcurrentMapCache("product-search"));

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
    }

    @Test
    @DisplayName("같은 키로 50명이 동시에 miss하면 loader는 1번, 50명 모두 같은 값을 받는다")
    void sameKeyLoadsOnce() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return cache.get("생수", () -> {
                    loads.incrementAndGet();
                    release.await(5, TimeUnit.SECONDS);   // 다른 요청이 모두 도착할 때까지 원본 조회가 끝나지 않게
                    return "결과";
                });
            }));
        }
        start.countDown();
        Thread.sleep(200);
        release.countDown();

        for (Future<String> future : futures) {
            assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("결과");
        }
        assertThat(loads).hasValue(1);
        assertThat(cache.get("생수").get()).isEqualTo("결과");
    }

    @Test
    @DisplayName("다른 키는 병렬: 한 키의 loader가 막혀 있어도 다른 키는 끝난다")
    void differentKeysInParallel() throws Exception {
        CountDownLatch blocked = new CountDownLatch(1);
        Future<String> slow = pool.submit(() -> cache.get("고등어", () -> {
            blocked.await(5, TimeUnit.SECONDS);
            return "느림";
        }));
        Thread.sleep(100);

        Future<String> fast = pool.submit(() -> cache.get("사과", () -> "빠름"));

        assertThat(fast.get(1, TimeUnit.SECONDS)).isEqualTo("빠름");
        assertThat(slow.isDone()).isFalse();
        blocked.countDown();
        assertThat(slow.get(5, TimeUnit.SECONDS)).isEqualTo("느림");
    }

    @Test
    @DisplayName("loader가 실패하면 기다리던 요청도 같은 예외, 캐시하지 않아 다음 호출은 다시 loader")
    void failureNotCached() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return cache.get("없음", () -> {
                    loads.incrementAndGet();
                    release.await(5, TimeUnit.SECONDS);
                    throw new IllegalStateException("404");
                });
            }));
        }
        start.countDown();
        Thread.sleep(200);
        release.countDown();

        for (Future<String> future : futures) {
            assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(Cache.ValueRetrievalException.class)
                    .hasRootCauseMessage("404");
        }
        assertThat(loads).hasValue(1);
        assertThat(cache.get("없음")).isNull();

        assertThat(cache.get("없음", () -> {
            loads.incrementAndGet();
            return "생김";
        })).isEqualTo("생김");
        assertThat(loads).hasValue(2);
    }

    @Test
    @DisplayName("캐시에 있으면 loader를 부르지 않는다")
    void hitSkipsLoader() {
        cache.put("생수", "캐시 값");

        String value = cache.get("생수", () -> {
            throw new AssertionError("loader를 부르면 안 된다");
        });

        assertThat(value).isEqualTo("캐시 값");
    }
}
