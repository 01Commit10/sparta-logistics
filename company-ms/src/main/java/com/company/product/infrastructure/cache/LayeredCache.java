package com.company.product.infrastructure.cache;

import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

/**
 * 2단 캐시: 서버 메모리(L1, Caffeine) → Redis(L2) → 원본(DB).
 * L1에 없으면 L2를 보고, L2에서 찾으면 L1에도 채운다. 쓰기·지우기는 두 단 모두.
 * 서버가 여러 대면 다른 서버의 L1은 지워지지 않아 L1 TTL만큼 옛 값이 보일 수 있다 (실험은 1대).
 */
public class LayeredCache implements Cache {

    private final Cache local;
    private final Cache remote;

    public LayeredCache(Cache local, Cache remote) {
        this.local = local;
        this.remote = remote;
    }

    @Override
    public String getName() {
        return remote.getName();
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper value = local.get(key);
        if (value != null) {
            return value;
        }
        value = remote.get(key);
        if (value != null) {
            local.put(key, value.get());
        }
        return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        ValueWrapper value = get(key);
        if (value == null || value.get() == null) {
            return null;
        }
        if (type != null && !type.isInstance(value.get())) {
            throw new IllegalStateException("캐시 값의 타입이 다릅니다: " + type.getName());
        }
        return (T) value.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper cached = get(key);
        if (cached != null) {
            return (T) cached.get();
        }
        T value;
        try {
            value = valueLoader.call();
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
        put(key, value);
        return value;
    }

    @Override
    public void put(Object key, Object value) {
        remote.put(key, value);
        local.put(key, value);
    }

    // L2를 먼저 지운다: L1만 먼저 지우면 그 사이 조회가 L2의 옛 값을 L1에 다시 채울 수 있다
    @Override
    public void evict(Object key) {
        remote.evict(key);
        local.evict(key);
    }

    @Override
    public void clear() {
        remote.clear();
        local.clear();
    }
}
