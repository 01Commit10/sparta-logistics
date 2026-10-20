package com.order.domain.client;

import java.util.UUID;

public record StockDecreaseCommand(
        UUID orderId,
        UUID hubId,
        UUID productId,
        int quantity) {
}