package com.order.infrastructure.client.hub;

import java.util.UUID;

public record DecreaseFeignRequest(UUID orderId, UUID hubId, UUID productId, int quantity) {
}
