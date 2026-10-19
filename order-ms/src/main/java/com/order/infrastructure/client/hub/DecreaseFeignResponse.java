package com.order.infrastructure.client.hub;

import java.util.UUID;

public record DecreaseFeignResponse(UUID operationId, UUID orderId, String status,
                                    UUID inventoryId, int changedQuantity, int remainingQuantity) {
}
