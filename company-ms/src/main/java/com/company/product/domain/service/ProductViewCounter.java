package com.company.product.domain.service;

import java.util.UUID;

/** 상품 상세 조회수 세기 (인기 상품용). 구현: DB 바로 반영 / Redis에 모아 주기 반영 */
public interface ProductViewCounter {

    /** 조회수를 1 올리고, 올라간 값을 돌려준다 */
    long increase(UUID productId);
}
