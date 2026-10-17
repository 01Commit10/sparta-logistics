package com.company.product.presentation.dto;

import com.company.product.application.dto.ProductSearchQuery;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 상품 목록 검색 조건 (쿼리 파라미터, 04 API 명세서 Product). 모두 선택이고 보내지 않으면 조건을 걸지 않는다.
 * name은 부분 일치라 상품명 최대 길이(150)를 넘으면 찾을 수 없으므로 400으로 막는다.
 * page·size·sort는 Pageable로 따로 받는다 (common PagePolicy).
 */
public record ProductSearchRequest(
        UUID companyId,
        @Size(max = 150) String name
) {
    public ProductSearchQuery toQuery() {
        return new ProductSearchQuery(companyId, name);
    }
}
