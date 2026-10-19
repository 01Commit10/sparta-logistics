package com.order.application.dto;

import com.order.domain.entity.Order;
import com.order.domain.vo.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderResult(
        UUID orderId,
        UUID requesterCompanyId,
        UUID supplierCompanyId,
        UUID productId,
        int quantity,
        BigDecimal unitPriceSnapshot,
        BigDecimal totalAmount,
        OrderStatus orderStatus,
        UUID deliveryId
) {
    public static OrderResult from(Order order) {
        return new OrderResult(
                order.getId(),
                order.getRequesterCompanyId(),
                order.getSupplierCompanyId(),
                order.getProductId(),
                order.getQuantity(),
                order.getUnitPriceSnapshot(),
                order.getTotalAmount(),
                order.getOrderStatus(),
                order.getDeliveryId()
        );
    }
}