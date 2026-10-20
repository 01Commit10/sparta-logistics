package com.company.product.infrastructure.viewcount;

import com.company.product.domain.entity.Product;
import com.company.product.domain.repository.ProductRepository;
import com.company.product.domain.service.ProductViewCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 조회할 때마다 DB에서 바로 +1 (캐시 없음 단계, 기본값). 동시 조회에도 누락이 없지만 인기 상품 행에 쓰기가 몰린다 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "product.cache.mode", havingValue = "none", matchIfMissing = true)
public class DbProductViewCounter implements ProductViewCounter {

    private final ProductRepository productRepository;

    @Override
    @Transactional
    public long increase(UUID productId) {
        productRepository.increaseViewCount(productId);
        // 벌크 UPDATE 뒤 영속성 컨텍스트를 비우므로 DB의 올라간 값을 다시 읽는다
        return productRepository.findById(productId).map(Product::getViewCount).orElse(0L);
    }
}
