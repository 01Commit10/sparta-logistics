package com.hub.inventory.domain.repository;

import com.hub.inventory.domain.dto.DecreaseResult;
import com.hub.inventory.domain.dto.RestoreResult;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface InventoryStockRepository {
    // 재고를 quantity만큼 차감
    Optional<DecreaseResult> decrease(
            UUID hubId,
            UUID productId,
            int quantity,
            UUID actorId,
            LocalDateTime changedAt
    );
    //원본 차감 수량만큼 재고 복원하고, 복원 직후 수량을 반환
    Optional<RestoreResult> restore(
            UUID inventoryId,
            int quantity,
            UUID actorId,
            LocalDateTime changedAt
    );
}
