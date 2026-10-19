package com.order.domain.client;

import java.util.UUID;

public record CreateDeliveryCommand(
        UUID orderId,             // 어떤 주문의 배송인지 (p_deliveries.order_id, UNIQUE)
        UUID departureHubId,      // 출발 허브 = 재고를 차감한 허브
        UUID supplierCompanyId,   // 보내는 업체
        UUID requesterCompanyId,  // 받는 업체 → 배송 서비스가 도착 허브·주소를 조회
        UUID recipientUserId     // 수령인 (주문자)
) {
}