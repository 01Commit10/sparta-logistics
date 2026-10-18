package com.hub.inventory.presentation.dto;

import com.hub.inventory.application.dto.DecreaseInventoryResult;

import java.util.UUID;

public record DecreaseInventoryResponseDto(
        UUID operationId,
        UUID orderId,
        String status,
        UUID inventoryId,
        int changedQuantity,
        int remainingQuantity
) {
    public static DecreaseInventoryResponseDto from(
            DecreaseInventoryResult result
    ) {
        return new DecreaseInventoryResponseDto(
                result.operationId(),
                result.orderId(),
                result.status().name(),
                result.inventoryId(),
                result.changedQuantity(),
                result.remainingQuantity()
        );
    }
}
