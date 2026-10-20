package com.company.product.infrastructure.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 같은 이름의 L1(local)·L2(remote) 캐시를 묶어 LayeredCache로 내준다 */
public class LayeredCacheManager implements CacheManager {

    private final CacheManager local;
    private final CacheManager remote;
    private final ConcurrentMap<String, Cache> caches = new ConcurrentHashMap<>();

    public LayeredCacheManager(CacheManager local, CacheManager remote) {
        this.local = local;
        this.remote = remote;
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
        Cache localCache = local.getCache(name);
        Cache remoteCache = remote.getCache(name);
        if (localCache == null || remoteCache == null) {
            return null;
        }
        return new LayeredCache(localCache, remoteCache);
    }
}
