package com.hub.inventory.domain.repository;

import com.hub.inventory.domain.dto.DecreaseResult;

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
}
