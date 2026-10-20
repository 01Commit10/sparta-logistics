package com.hub.inventory.application.dto;

import com.hub.inventory.domain.entity.InventoryOperation;
import com.hub.inventory.domain.vo.InventoryOperationStatus;
import com.hub.inventory.domain.vo.InventoryOperationType;

import java.util.UUID;

public record RestoreInventoryResult(
        UUID operationId,
        UUID orderId,
        InventoryOperationStatus status,
        UUID inventoryId,
        int changedQuantity,
        int remainingQuantity
) {
    // 저장된 복원 성공 기록을 응용 계층의 처리 결과로 변환한다.
    public static RestoreInventoryResult fromEntity(
            InventoryOperation operation
    ) {
        if (operation == null
                || operation.getId() == null
                || operation.getOperationType()
                != InventoryOperationType.RESTORE
                || operation.getOperationStatus()
                != InventoryOperationStatus.SUCCEEDED
                || operation.getRemainingQuantity() == null) {
            throw new IllegalArgumentException(
                    "저장된 복원 성공 작업이 필요합니다."
            );
        }

        return new RestoreInventoryResult(
                operation.getId(),
                operation.getOrderId(),
                operation.getOperationStatus(),
                operation.getInventoryId(),
                operation.getRequestedQuantity(),
                operation.getRemainingQuantity()
        );
    }
}