package com.company.product.application.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * product.hot.* (application.yaml, hot 모드 전용 · P03).
 * size: 스냅샷에 담을 인기 상품 수(= 인기 상품 요청의 최대 size), candidate-size: 순위에서 읽을 후보 수(삭제된 상품 거르기용),
 * refresh-interval: 스냅샷 덮어쓰기 주기, local-ttl: 스케줄러가 멈췄을 때 옛 스냅샷을 버리는 안전 TTL,
 * warmup-size: 기동 시 순위가 비었으면 DB에서 넣을 상위 상품 수
 */
@ConfigurationProperties(prefix = "product.hot")
public record ProductHotProperties(
        @DefaultValue("10") int size,
        @DefaultValue("30") int candidateSize,
        @DefaultValue("5s") Duration refreshInterval,
        @DefaultValue("1m") Duration localTtl,
        @DefaultValue("1000") int warmupSize
) {
    public ProductHotProperties {
        if (candidateSize < size) {
            throw new IllegalArgumentException(
                    "product.hot.candidate-size(%d)는 size(%d) 이상이어야 합니다.".formatted(candidateSize, size));
        }
        if (localTtl.compareTo(refreshInterval) <= 0) {
            throw new IllegalArgumentException(
                    "product.hot.local-ttl(%s)은 refresh-interval(%s)보다 길어야 합니다.".formatted(localTtl, refreshInterval));
        }
    }
}
