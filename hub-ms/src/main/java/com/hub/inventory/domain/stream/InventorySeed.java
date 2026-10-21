package com.hub.inventory.domain.stream;
import java.util.UUID;
public record InventorySeed(UUID inventoryId, UUID hubId, UUID productId, int quantity, boolean deleted) { }
