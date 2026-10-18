package com.hub.inventory.application.dto;

import com.hub.inventory.domain.entity.Inventory;

import java.util.UUID;

public record InventoryResult(
        UUID id,
        UUID hubId,
        UUID productId,
        int quantity
) {

    public static InventoryResult fromEntity(Inventory inventory){
        return new InventoryResult(
                inventory.getId(),
                inventory.getHubId(),
                inventory.getProductId(),
                inventory.getQuantity()
        );
    }
}
