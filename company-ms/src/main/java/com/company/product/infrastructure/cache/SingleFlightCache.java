package com.company.product.infrastructure.cache;

import org.springframework.cache.Cache;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 키 단위 single-flight 캐시 (P03). 같은 키가 동시에 miss하면 첫 요청만 원본을 읽고 나머지는 그 결과를 기다린다.
 * 다른 키는 서로 기다리지 않는다. RedisCache의 기본 writer는 잠금이 없어 @Cacheable(sync = true)만으로는
 * 스탬피드가 그대로이고, locking writer는 캐시 이름 단위로 잠가 모든 검색이 한 줄로 서기 때문에 서버 안에서 직접 막는다.
 * 서버가 여러 대면 서버마다 1번씩 원본을 읽는다.
 */
public class SingleFlightCache implements Cache {

    private final Cache delegate;
    private final ConcurrentMap<Object, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

    public SingleFlightCache(Cache delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        return delegate.get(key);
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        return delegate.get(key, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper cached = delegate.get(key);
        if (cached != null) {
            return (T) cached.get();
        }
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> running = inFlight.putIfAbsent(key, mine);
        if (running != null) {
            return (T) await(running);
        }
        try {
            // 앞선 요청이 방금 채웠을 수 있어 한 번 더 본다
            ValueWrapper filled = delegate.get(key);
            Object value = filled != null ? filled.get() : load(key, valueLoader);
            mine.complete(value);
            return (T) value;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private Object load(Object key, Callable<?> valueLoader) {
        Object value;
        try {
            value = valueLoader.call();
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
        delegate.put(key, value);
        return value;
    }

    private static Object await(CompletableFuture<Object> running) {
        try {
            return running.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    @Override
    public void put(Object key, Object value) {
        delegate.put(key, value);
    }

    @Override
    public void evict(Object key) {
        delegate.evict(key);
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}
