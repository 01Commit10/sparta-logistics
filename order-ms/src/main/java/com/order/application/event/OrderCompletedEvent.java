package com.order.application.event;

import com.order.domain.entity.Order;

import java.util.UUID;

/**
 * 주문 완료(PROCESSING -> COMPLETED) 사실
 * 알림에 필요한 값만 담는다. (요청사항 원문 제외)
 */
public record OrderCompletedEvent(
        UUID orderId,
        UUID productId,
        int quantity,
        UUID deliveryId,
        UUID orderedBy,
        boolean hasRequestNote
) {
    public static OrderCompletedEvent from(Order order) {
        return new OrderCompletedEvent(
                order.getId(), order.getProductId(), order.getQuantity(),
                order.getDeliveryId(), order.getOrderedBy(),
                order.getRequestNote() != null && !order.getRequestNote().isBlank());
    }
}
