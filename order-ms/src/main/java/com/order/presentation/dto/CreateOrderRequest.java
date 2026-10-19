package com.order.presentation.dto;

import com.order.application.dto.CreateOrderCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID requesterCompanyId,
        @NotNull UUID supplierCompanyId,
        @NotNull UUID productId,
        @NotNull @Positive Integer quantity,
        @NotNull @PositiveOrZero BigDecimal unitPrice,
        String requestNote
) {
    public CreateOrderCommand toCommand(UUID orderedBy, String idempotencyKey) {
        return new CreateOrderCommand(requesterCompanyId, supplierCompanyId, productId,
                quantity, unitPrice, requestNote, orderedBy, idempotencyKey);
    }
}