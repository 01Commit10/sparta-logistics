package com.company.product.application.service;

import com.common.exception.BusinessException;
import com.company.product.application.cache.HotProductCache;
import com.company.product.application.cache.ProductHotProperties;
import com.company.product.application.dto.ProductResult;
import com.company.product.domain.repository.ProductRepository;
import com.company.product.domain.service.ProductRanking;
import com.company.product.domain.service.RankedProduct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 인기 상품 스냅샷을 주기적으로 새로 만들어 덮어쓴다 (P03 2.3, hot 모드 전용).
 * 순위 후보 → 상세(Redis 상세 캐시 → 없으면 DB) → 앞에서부터 N개. 비우지 않고 덮어써서 TTL 만료 순간의 스탬피드가 없다.
 * 실패하면 기존 스냅샷을 그대로 둔다 (안전 TTL이 지나면 TOP N 요청은 기존 검색 경로로 간다).
 */
@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "product.cache.mode", havingValue = "hot")
public class HotProductRefresher {

    private final ProductRanking ranking;
    private final ProductQueryService productQueryService;
    private final ProductRepository productRepository;
    private final HotProductCache hotProductCache;
    private final ProductHotProperties properties;

    // 기동 직후: 순위가 비었으면 DB 조회수 상위로 채우고(여러 서버가 동시에 떠도 점수를 덮지 않음) 바로 1번 갱신
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        try {
            ranking.warmUpIfEmpty(productRepository.findTopByViewCount(properties.warmupSize()));
        } catch (RuntimeException e) {
            log.warn("인기 상품 순위 초기화 실패: {}", e.toString());
        }
        refresh();
    }

    @Scheduled(fixedDelayString = "${product.hot.refresh-interval:5s}")
    public void refresh() {
        try {
            hotProductCache.replace(load());
        } catch (RuntimeException e) {
            log.warn("인기 상품 스냅샷 갱신 실패, 기존 스냅샷 유지: {}", e.toString());
        }
    }

    // 삭제된 상품·업체(404)는 건너뛰고 순위에서 뺀다. 조회수는 순위 점수(= 지금 누적 조회수)로 채운다
    private List<ProductResult> load() {
        List<ProductResult> products = new ArrayList<>();
        for (RankedProduct candidate : ranking.top(properties.candidateSize())) {
            if (products.size() == properties.size()) {
                break;
            }
            try {
                products.add(productQueryService.getActiveProduct(candidate.productId())
                        .withViewCount(candidate.viewCount()));
            } catch (BusinessException notFound) {
                ranking.remove(candidate.productId());
            }
        }
        return products;
    }
}
