package com.order.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderCommand(
        UUID requesterCompanyId,  // 받는 업체 (요청 body)
        UUID supplierCompanyId,   // 보내는 업체 (요청 body)
        UUID productId,           // 상품 (요청 body)
        int quantity,             // 수량 (요청 body)
        BigDecimal unitPrice,     // 단가 (요청 body, 업체 서비스 미호출 결정)
        String requestNote,       // 요청사항 (요청 body)
        UUID orderedBy,           // 주문자 (헤더 X-User-Id)
        String idempotencyKey     // 멱등성 키 (헤더 Idempotency-Key)
) {

}
