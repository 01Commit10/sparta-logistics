package com.company.product.infrastructure.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Map;

/**
 * product.cache.* (application.yaml).
 * ttl: Redis(L2) 캐시별 TTL (없으면 common 기본 10분), local: Caffeine(L1) TTL·최대 건수 (모든 캐시 공통)
 */
@ConfigurationProperties(prefix = "product.cache")
public record ProductCacheProperties(
        @DefaultValue("none") ProductCacheMode mode,
        Map<String, Duration> ttl,
        @DefaultValue Local local
) {
    public ProductCacheProperties {
        ttl = ttl == null ? Map.of() : Map.copyOf(ttl);
    }

    public record Local(@DefaultValue("10s") Duration ttl, @DefaultValue("10000") long maximumSize) {
    }
}
