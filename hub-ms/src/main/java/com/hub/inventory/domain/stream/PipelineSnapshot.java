package com.hub.inventory.domain.stream;
import java.util.List;
import java.util.UUID;
/** DB state at a committed checkpoint; operations contains the complete historical idempotency ledger. */
public record PipelineSnapshot(UUID epoch, long sequence, String streamId,
        List<InventorySeed> inventories, List<InventoryStreamEvent> operations) {
    public PipelineSnapshot { inventories = List.copyOf(inventories); operations = List.copyOf(operations); }
}
