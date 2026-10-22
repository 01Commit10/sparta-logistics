package com.order.infrastructure.client.hub;

import java.util.UUID;

public record InventoryFeignResponse(UUID id, UUID hubId, UUID productId, int quantity
) {
}
