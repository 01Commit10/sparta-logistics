package com.hub.inventory.application.dto;

import com.hub.inventory.domain.entity.InventoryOperation;
import com.hub.inventory.domain.vo.InventoryOperationStatus;
import com.hub.inventory.domain.vo.InventoryRejectionReason;

import java.util.UUID;

public record DecreaseInventoryResult(
        UUID operationId,
        UUID orderId,
        InventoryOperationStatus status,
        UUID inventoryId,
        int changedQuantity,
        Integer remainingQuantity,
        InventoryRejectionReason rejectionReason
) {
    //정적 팩토리 메소드
    public static DecreaseInventoryResult fromEntity(
            InventoryOperation operation
    ) {
        boolean succeeded =
                operation.getOperationStatus()
                        == InventoryOperationStatus.SUCCEEDED;

        return new DecreaseInventoryResult(
                operation.getId(),
                operation.getOrderId(),
                operation.getOperationStatus(),
                operation.getInventoryId(),
                succeeded ? -operation.getRequestedQuantity() : 0,
                operation.getRemainingQuantity(),
                operation.getRejectionReason()
        );
    }

}
