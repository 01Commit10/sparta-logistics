package com.hub.inventory.infrastructure.persistence;

import com.hub.inventory.domain.entity.InventoryOperation;
import com.hub.inventory.domain.repository.InventoryOperationRepository;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryOperationRepositoryImpl implements InventoryOperationRepository {

    private final InventoryOperationJpaRepository inventoryOperationJpaRepository;

    public InventoryOperationRepositoryImpl(InventoryOperationJpaRepository inventoryOperationJpaRepository) {
        this.inventoryOperationJpaRepository = inventoryOperationJpaRepository;
    }

    @Override
    public InventoryOperation save(InventoryOperation inventoryOperation) {
        return inventoryOperationJpaRepository.save(inventoryOperation);
    }
}
