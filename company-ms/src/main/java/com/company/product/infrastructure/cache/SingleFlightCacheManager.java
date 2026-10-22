package com.company.product.infrastructure.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 감싼 CacheManager의 캐시를 SingleFlightCache로 내준다 (hot 모드: Redis 캐시 + 키 단위 single-flight) */
public class SingleFlightCacheManager implements CacheManager {

    private final CacheManager delegate;
    private final ConcurrentMap<String, Cache> caches = new ConcurrentHashMap<>();

    public SingleFlightCacheManager(CacheManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, this::createCache);
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(caches.keySet());
    }

    private Cache createCache(String name) {
        Cache cache = delegate.getCache(name);
        return cache == null ? null : new SingleFlightCache(cache);
    }
}
