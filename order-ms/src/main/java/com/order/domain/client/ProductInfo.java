package com.order.domain.client;

import java.math.BigDecimal;
import java.util.UUID;

// company-ms 상품 조회 결과 중 주문에 필요한 값
public record ProductInfo(
        UUID productId,
        UUID companyId,
        String name,
        BigDecimal unitPrice
) {
    // [공급업체 확인]
    public boolean isSuppliedBy(UUID supplierCompanyId) {
        return companyId.equals(supplierCompanyId);
    }

    // 1000과 1000.00을 같게 보려고 equals가 아니라 compareTo
    public boolean isPricedAt(BigDecimal price) {
        return unitPrice.compareTo(price) == 0;
    }
}