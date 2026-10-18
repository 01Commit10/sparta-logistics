package com.hub.inventory.presentation.dto;

import com.hub.inventory.application.dto.DecreaseInventoryCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record DecreaseInventoryRequestDto(
        @NotNull UUID orderId,
        @NotNull UUID hubId,
        @NotNull UUID productId,
        @NotNull @Positive Integer quantity
) {

    public DecreaseInventoryCommand toCommand(String idempotencyKey) {
        return new DecreaseInventoryCommand(
                orderId,
                hubId,
                productId,
                quantity,
                idempotencyKey
        );
    }
}