package com.company.product.infrastructure.cache;

import com.common.config.CommonRedisAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** product.cache.mode에 따라 CacheManager가 바뀐다 (Redis에 실제로 붙지는 않는다) */
class ProductCacheConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProductCacheConfig.class)
            .withBean(RedisConnectionFactory.class, LettuceConnectionFactory::new)
            .withBean(RedisCacheConfiguration.class,
                    () -> new CommonRedisAutoConfiguration().redisCacheConfiguration("company-ms"));

    @Test
    @DisplayName("기본(none): 캐시 없음")
    void none() {
        runner.run(context -> assertThat(context.getBean(CacheManager.class)).isInstanceOf(NoOpCacheManager.class));
    }

    @Test
    @DisplayName("redis: RedisCacheManager, 캐시별 TTL은 product.cache.ttl을 따른다")
    void redis() {
        runner.withPropertyValues("product.cache.mode=redis", "product.cache.ttl.product-search=30s")
                .run(context -> {
                    CacheManager manager = context.getBean(CacheManager.class);
                    assertThat(manager).isInstanceOf(RedisCacheManager.class);
                    RedisCache search = (RedisCache) manager.getCache("product-search");
                    assertThat(search.getCacheConfiguration().getTtlFunction().getTimeToLive("k", "v"))
                            .isEqualTo(Duration.ofSeconds(30));
                    RedisCache other = (RedisCache) manager.getCache("product-detail");
                    assertThat(other.getCacheConfiguration().getTtlFunction().getTimeToLive("k", "v"))
                            .isEqualTo(Duration.ofMinutes(10));
                });
    }

    @Test
    @DisplayName("layered: Caffeine(L1) + Redis(L2)")
    void layered() {
        runner.withPropertyValues("product.cache.mode=layered")
                .run(context -> {
                    CacheManager manager = context.getBean(CacheManager.class);
                    assertThat(manager).isInstanceOf(LayeredCacheManager.class);
                    assertThat(manager.getCache("product-detail")).isInstanceOf(LayeredCache.class);
                });
    }

    @Test
    @DisplayName("hot: Redis 캐시(상세·검색)를 키 단위 single-flight로 감싸고, Caffeine L1 캐시는 만들지 않는다")
    void hot() {
        runner.withPropertyValues("product.cache.mode=hot", "product.cache.ttl.product-search=30s")
                .run(context -> {
                    CacheManager manager = context.getBean(CacheManager.class);
                    assertThat(manager).isInstanceOf(SingleFlightCacheManager.class);
                    assertThat(manager.getCache("product-detail")).isInstanceOf(SingleFlightCache.class);
                    Cache search = manager.getCache("product-search");
                    assertThat(search).isInstanceOf(SingleFlightCache.class);
                    assertThat(search.getNativeCache()).isInstanceOf(RedisCacheWriter.class);
                    assertThat(manager.getCache("product-search")).isSameAs(search);
                });
    }
}
