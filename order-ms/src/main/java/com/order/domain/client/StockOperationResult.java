package com.order.domain.client;

import java.util.UUID;

public record StockOperationResult(UUID operationId,
                                   boolean succeeded,
                                   String errorCode,
                                   int remainingQuantity) {

    public boolean isInsufficientStock() {
        return !succeeded && "INSUFFICIENT_STOCK".equals(errorCode);
    }
}
