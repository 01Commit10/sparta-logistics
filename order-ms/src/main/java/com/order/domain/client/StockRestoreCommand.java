package com.order.domain.client;

import java.util.UUID;

public record StockRestoreCommand(UUID orderId) {
}