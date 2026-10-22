package com.hub.inventory.presentation.dto;

import com.hub.inventory.application.dto.RestoreInventoryResult;

import java.util.UUID;

public record RestoreInventoryResponseDto(
        UUID operationId,
        UUID orderId,
        String status,
        UUID inventoryId,
        int changedQuantity,
        int remainingQuantity
) {
    public static RestoreInventoryResponseDto from(
            RestoreInventoryResult result
    ) {
        return new RestoreInventoryResponseDto(
                result.operationId(),
                result.orderId(),
                result.status().name(),
                result.inventoryId(),
                result.changedQuantity(),
                result.remainingQuantity()
        );
    }
}