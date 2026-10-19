package com.hub.inventory.domain.dto;

import java.util.UUID;

public record DecreaseResult(
        UUID inventoryId,
        int remainingQuantity
) {
}