package com.company.product.domain.service;

import java.util.UUID;

/** 인기 순위의 한 칸: 상품 ID와 누적 조회수(점수) */
public record RankedProduct(UUID productId, long viewCount) {
}
