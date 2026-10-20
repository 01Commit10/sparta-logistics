package com.hub.inventory.domain.dto;

import java.util.UUID;

public record RestoreResult(
        // 복원할 재고
        UUID inventoryId,
        // 복원 직후의 수량
        int remainingQuantity
) {
}