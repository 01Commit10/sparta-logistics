package com.hub.inventory.domain.repository;

import com.hub.inventory.domain.entity.InventoryOperation;

public interface InventoryOperationRepository {
    InventoryOperation save(InventoryOperation inventoryOperation);
}
