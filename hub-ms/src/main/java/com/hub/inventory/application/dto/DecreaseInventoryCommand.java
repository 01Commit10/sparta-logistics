package com.hub.inventory.application.dto;

import java.util.UUID;

public record DecreaseInventoryCommand(
        UUID orderId,
        UUID hubId,
        UUID productId,
        int quantity,
        String idempotencyKey
) {
}
