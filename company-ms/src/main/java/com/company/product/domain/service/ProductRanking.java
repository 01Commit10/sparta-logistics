package com.company.product.domain.service;

import java.util.List;
import java.util.UUID;

/** 조회수 기준 인기 순위 (P03, hot 모드). 점수는 조회수를 셀 때 같이 갱신된다 (ProductViewCounter 구현) */
public interface ProductRanking {

    /** 조회수가 높은 순으로 최대 count개 */
    List<RankedProduct> top(int count);

    /** 순위에서 뺀다 (삭제된 상품·업체) */
    void remove(UUID productId);

    /** 순위가 비었을 때만 넣는다. 이미 있는 점수는 덮지 않는다 (여러 서버가 동시에 기동해도 안전) */
    void warmUpIfEmpty(List<RankedProduct> products);
}
