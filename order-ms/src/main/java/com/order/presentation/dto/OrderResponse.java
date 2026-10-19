package com.order.presentation.dto;

import com.order.application.dto.OrderResult;
import com.order.domain.vo.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderResponse(
        UUID orderId, UUID requesterCompanyId, UUID supplierCompanyId, UUID productId,
        int quantity, BigDecimal unitPriceSnapshot, BigDecimal totalAmount,
        OrderStatus orderStatus, UUID deliveryId
) {
    public static OrderResponse from(OrderResult result) {
        return new OrderResponse(result.orderId(), result.requesterCompanyId(), result.supplierCompanyId(),
                result.productId(), result.quantity(), result.unitPriceSnapshot(), result.totalAmount(),
                result.orderStatus(), result.deliveryId());
    }
}