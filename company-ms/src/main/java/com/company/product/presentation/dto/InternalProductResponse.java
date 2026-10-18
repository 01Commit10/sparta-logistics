package com.company.product.presentation.dto;

import com.company.product.application.dto.ProductResult;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 서비스 간 계약 응답 (04 API 명세서 Internal API "상품 검증", 소비자: order-ms). 외부 API 응답과 섞어 쓰지 않는다.
 * companyId: 공급업체 소유 확인, unitPrice: 주문 단가 스냅샷, name: 주문 알림 메시지 (07 서비스 정책 5장).
 * 필드를 바꾸면 호출하는 서비스의 Feign DTO도 함께 바꿔야 한다.
 */
public record InternalProductResponse(
        UUID id,
        UUID companyId,
        String name,
        BigDecimal unitPrice
) {

    public static InternalProductResponse from(ProductResult result) {
        return new InternalProductResponse(result.id(), result.companyId(), result.name(), result.unitPrice());
    }
}
