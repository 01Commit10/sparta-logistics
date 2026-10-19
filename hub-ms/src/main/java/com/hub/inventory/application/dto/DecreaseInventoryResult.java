package com.hub.inventory.application.dto;

import com.hub.inventory.domain.vo.InventoryOperationStatus;

import java.util.UUID;

public record DecreaseInventoryResult(
        UUID operationId,
        UUID orderId,
        InventoryOperationStatus status,
        UUID inventoryId,
        int changedQuantity,
        int remainingQuantity
) {
}
