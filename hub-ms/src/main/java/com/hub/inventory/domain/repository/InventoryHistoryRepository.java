package com.hub.inventory.domain.repository;

import com.hub.inventory.domain.entity.InventoryHistory;

public interface InventoryHistoryRepository {

    InventoryHistory save(InventoryHistory history);
}