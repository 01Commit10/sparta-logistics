package com.hub.inventory.domain.repository;

import com.hub.inventory.domain.entity.InventoryOperation;

import java.util.Optional;
import java.util.UUID;

public interface InventoryOperationRepository {
    InventoryOperation save(InventoryOperation inventoryOperation);

    Optional<InventoryOperation> findByIdempotencyKey( String idempotencyKey);

    Optional<InventoryOperation> findById(UUID operationId);

    boolean existsByOriginalOperationId(UUID originalOperationId);
}
