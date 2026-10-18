package com.hub.inventory.presentation.dto;

import com.hub.inventory.application.dto.InventoryResult;

import java.util.UUID;

public record InventoryResponseDto(
        UUID id,
        UUID hubId,
        UUID productId,
        int quantity
) {
    public static InventoryResponseDto from(InventoryResult result){
        return new InventoryResponseDto(
                result.id(),
                result.hubId(),
                result.productId(),
                result.quantity()
        );
    }
}
