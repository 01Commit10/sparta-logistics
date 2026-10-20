package com.order.infrastructure.client.delivery;

import java.util.UUID;

// ⚠️ delivery-ms 현재 브랜치는 arrivalHubId, deliveryAddress, recipientName을 요구함 → 영은님과 확정 후 수정
public record DeliveryFeignRequest(UUID orderId, UUID departureHubId, UUID supplierCompanyId,
                                   UUID requesterCompanyId, UUID recipientUserId) {
}
