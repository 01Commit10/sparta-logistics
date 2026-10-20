package com.company.product.infrastructure.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** L1(서버 메모리) → L2(Redis) 2단 캐시. 두 단 모두 메모리 캐시로 바꿔 순서만 확인한다 */
class LayeredCacheTest {

    /** 몇 번 찾아봤는지 세는 캐시 */
    static class CountingCache extends ConcurrentMapCache {
        final AtomicInteger lookups = new AtomicInteger();

        CountingCache(String name) {
            super(name);
        }

        @Override
        protected Object lookup(Object key) {
            lookups.incrementAndGet();
            return super.lookup(key);
        }
    }

    private final CountingCache local = new CountingCache("product-detail");
    private final CountingCache remote = new CountingCache("product-detail");
    private final LayeredCache cache = new LayeredCache(local, remote);

    @Test
    @DisplayName("L1에 있으면 L2(Redis)는 보지 않는다")
    void localHit() {
        local.put("k", "v1");

        assertThat(cache.get("k").get()).isEqualTo("v1");
        assertThat(remote.lookups).hasValue(0);
    }

    @Test
    @DisplayName("L1에 없고 L2에 있으면 L2 값을 돌려주고 L1에도 채운다 (다음은 L1에서)")
    void remoteHitFillsLocal() {
        remote.put("k", "v1");

        assertThat(cache.get("k").get()).isEqualTo("v1");
        assertThat(local.get("k").get()).isEqualTo("v1");

        cache.get("k");
        assertThat(remote.lookups).hasValue(1);
    }

    @Test
    @DisplayName("두 단 모두 없으면 null (Spring이 원본을 읽어 put한다)")
    void miss() {
        assertThat(cache.get("k")).isNull();
        assertThat(cache.get("k", String.class)).isNull();
    }

    @Test
    @DisplayName("put은 두 단 모두에 쓴다")
    void putBoth() {
        cache.put("k", "v1");

        assertThat(local.get("k").get()).isEqualTo("v1");
        assertThat(remote.get("k").get()).isEqualTo("v1");
    }

    @Test
    @DisplayName("evict·clear는 두 단 모두에서 지운다")
    void evictBoth() {
        cache.put("k", "v1");
        cache.put("k2", "v2");

        cache.evict("k");
        assertThat(local.get("k")).isNull();
        assertThat(remote.get("k")).isNull();

        cache.clear();
        assertThat(local.get("k2")).isNull();
        assertThat(remote.get("k2")).isNull();
    }

    @Test
    @DisplayName("loader 방식: 없을 때만 한 번 읽어 두 단에 채우고, 다음엔 읽지 않는다")
    void loader() {
        AtomicInteger loads = new AtomicInteger();

        String first = cache.get("k", () -> "v" + loads.incrementAndGet());
        String second = cache.get("k", () -> "v" + loads.incrementAndGet());

        assertThat(first).isEqualTo("v1");
        assertThat(second).isEqualTo("v1");
        assertThat(loads).hasValue(1);
        assertThat(remote.get("k").get()).isEqualTo("v1");
    }

    @Test
    @DisplayName("Manager: 같은 이름의 L1·L2를 묶어 내주고, 같은 이름이면 같은 캐시다")
    void manager() {
        LayeredCacheManager manager = new LayeredCacheManager(
                new org.springframework.cache.concurrent.ConcurrentMapCacheManager(),
                new org.springframework.cache.concurrent.ConcurrentMapCacheManager());

        assertThat(manager.getCache("product-detail")).isInstanceOf(LayeredCache.class)
                .isSameAs(manager.getCache("product-detail"));
        assertThat(manager.getCacheNames()).contains("product-detail");
    }
}
