package com.hub.inventory.infrastructure.persistence;

import com.hub.inventory.domain.entity.InventoryHistory;
import com.hub.inventory.domain.repository.InventoryHistoryRepository;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryHistoryRepositoryImpl implements InventoryHistoryRepository {

    private final InventoryHistoryJpaRepository inventoryHistoryJpaRepository;

    public InventoryHistoryRepositoryImpl(InventoryHistoryJpaRepository inventoryHistoryJpaRepository) {
        this.inventoryHistoryJpaRepository = inventoryHistoryJpaRepository;
    }

    @Override
    public InventoryHistory save(InventoryHistory history) {
        return inventoryHistoryJpaRepository.save(history);
    }
}