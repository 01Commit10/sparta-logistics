package com.company.product.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * product.cache.mode에 따라 CacheManager를 고른다 (P01 실험 단계 전환).
 * 이 빈이 있으면 Boot의 기본 RedisCacheManager는 만들어지지 않는다. Redis 직렬화·키 prefix는 common 설정을 그대로 쓴다.
 */
@Configuration
@EnableConfigurationProperties(ProductCacheProperties.class)
public class ProductCacheConfig {

    @Bean
    public CacheManager cacheManager(ProductCacheProperties properties,
                                     ObjectProvider<RedisConnectionFactory> connectionFactory,
                                     ObjectProvider<RedisCacheConfiguration> redisCacheConfiguration) {
        return switch (properties.mode()) {
            case NONE -> new NoOpCacheManager();
            case REDIS -> redisCacheManager(properties, connectionFactory.getObject(),
                    redisCacheConfiguration.getObject());
            case LAYERED -> new LayeredCacheManager(caffeineCacheManager(properties),
                    redisCacheManager(properties, connectionFactory.getObject(), redisCacheConfiguration.getObject()));
        };
    }

    private static RedisCacheManager redisCacheManager(ProductCacheProperties properties,
                                                       RedisConnectionFactory connectionFactory,
                                                       RedisCacheConfiguration defaults) {
        Map<String, RedisCacheConfiguration> perCache = properties.ttl().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> defaults.entryTtl(entry.getValue())));
        RedisCacheManager manager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                .build();
        manager.afterPropertiesSet();   // 빈이 아니라 직접 만든 것이라 초기화를 직접 부른다
        return manager;
    }

    // L1은 서버 메모리라 짧은 TTL과 최대 건수로 묶는다 (모든 캐시 공통)
    private static CaffeineCacheManager caffeineCacheManager(ProductCacheProperties properties) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(properties.local().ttl())
                .maximumSize(properties.local().maximumSize())
                .recordStats());
        return manager;
    }
}
