package com.order.domain.client;

import java.util.UUID;

public record InventoryInfo(
        UUID inventoryId,
        UUID hubId,
        UUID productId,
        int quantity
) {
    public boolean hasEnough(int requestQuantity) {
        return quantity >= requestQuantity;
    }
}