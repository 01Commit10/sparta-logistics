package com.hub.inventory.infrastructure.persistence;

import com.hub.inventory.domain.entity.Inventory;
import com.hub.inventory.domain.entity.InventoryOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryOperationJpaRepository extends JpaRepository<InventoryOperation, UUID> {

    Optional<InventoryOperation> findByIdempotencyKey( String idempotencyKey );

    boolean existsByOriginalOperationId(UUID originalOperationId);

}
