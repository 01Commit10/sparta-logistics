package com.hub.inventory.domain.stream;

import com.hub.inventory.domain.vo.InventoryOperationStatus;
import com.hub.inventory.domain.vo.InventoryOperationType;
import com.hub.inventory.domain.vo.InventoryRejectionReason;
import java.time.LocalDateTime;
import java.util.UUID;

/** Complete immutable operation payload; Redis Stream transport ID belongs to StreamEntry. */
public record InventoryStreamEvent(
        UUID epoch, long sequence, UUID operationId, String idempotencyKey,
        UUID originalOperationId, UUID orderId, UUID hubId, UUID productId,
        UUID inventoryId, InventoryOperationType operationType,
        InventoryOperationStatus operationStatus, InventoryRejectionReason rejectionReason,
        int requestedQuantity, Integer remainingQuantity, UUID actorId,
        LocalDateTime occurredAt, LocalDateTime createdAt, UUID createdBy,
        LocalDateTime updatedAt, UUID updatedBy) {
    public int stockChange() {
        if (operationStatus != InventoryOperationStatus.SUCCEEDED) return 0;
        return operationType == InventoryOperationType.RESTORE ? requestedQuantity : -requestedQuantity;
    }
}
