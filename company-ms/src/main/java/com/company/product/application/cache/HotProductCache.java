package com.company.product.application.cache;

import com.company.product.application.dto.ProductResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * 서버 메모리(L1)의 인기 상품 TOP N 스냅샷 (P03). 비우지 않고 새 스냅샷으로 통째 덮어써서 비는 순간이 없고,
 * 읽는 쪽은 잠금 없이 참조 하나만 읽는다. 스케줄러(HotProductRefresher)가 멈추면 안전 TTL(local-ttl) 뒤 비어서
 * 옛 순위를 계속 주지 않는다. hot 모드가 아니면 아무도 채우지 않아 늘 비어 있다.
 * 서버가 여러 대면 다른 서버의 스냅샷은 다음 갱신 때 바뀐다 (P03 D4).
 */
@Component
@EnableConfigurationProperties(ProductHotProperties.class)
public class HotProductCache {

    private record Snapshot(List<ProductResult> products, Map<UUID, ProductResult> byId, long createdAt) {

        static Snapshot of(List<ProductResult> products, long createdAt) {
            Map<UUID, ProductResult> byId = new LinkedHashMap<>();
            products.forEach(product -> byId.put(product.id(), product));
            return new Snapshot(List.copyOf(products), Map.copyOf(byId), createdAt);
        }

        Snapshot without(UUID productId) {
            return of(products.stream().filter(product -> !product.id().equals(productId)).toList(), createdAt);
        }
    }

    private static final Snapshot EMPTY = new Snapshot(List.of(), Map.of(), Long.MIN_VALUE);

    private final int maxSize;
    private final long ttlNanos;
    private final LongSupplier ticker;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(EMPTY);

    @Autowired
    public HotProductCache(ProductHotProperties properties) {
        this(properties.size(), properties.localTtl(), System::nanoTime);
    }

    public HotProductCache(int maxSize, Duration localTtl, LongSupplier ticker) {
        this.maxSize = maxSize;
        this.ttlNanos = localTtl.toNanos();
        this.ticker = ticker;
    }

    /** 인기 상품 요청으로 볼 최대 size */
    public int maxSize() {
        return maxSize;
    }

    public Optional<ProductResult> find(UUID productId) {
        return Optional.ofNullable(current().byId().get(productId));
    }

    /** 앞에서부터 size개. 비었으면(갱신 전·TTL 지남) 빈 목록 */
    public List<ProductResult> top(int size) {
        List<ProductResult> products = current().products();
        return products.size() <= size ? products : products.subList(0, size);
    }

    /** 새 스냅샷으로 통째 교체 (앞에서부터 maxSize개) */
    public void replace(List<ProductResult> products) {
        List<ProductResult> limited = products.size() <= maxSize ? products : products.subList(0, maxSize);
        snapshot.set(Snapshot.of(limited, ticker.getAsLong()));
    }

    /** 수정·삭제된 상품을 뺀다. 다음 갱신 때 새 값으로 다시 들어온다 */
    public void invalidate(UUID productId) {
        snapshot.updateAndGet(current -> current.byId().containsKey(productId) ? current.without(productId) : current);
    }

    private Snapshot current() {
        Snapshot current = snapshot.get();
        if (current == EMPTY || ticker.getAsLong() - current.createdAt() > ttlNanos) {
            return EMPTY;
        }
        return current;
    }
}
