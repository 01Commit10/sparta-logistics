package com.hub.inventory.application.service;
import com.hub.inventory.application.dto.InventoryResult;
import java.util.List;
import java.util.UUID;
/** Lifecycle hooks run only after the base DB transaction has ended. */
public interface InventoryLiveState {
    List<InventoryResult> refresh(List<InventoryResult> inventories);
    void registered(InventoryResult inventory);
    void beforeDelete(UUID productId);
}
