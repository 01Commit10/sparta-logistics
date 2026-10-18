package com.hub.inventory.application.dto;

import java.util.UUID;

public record CreateInventoryCommand(
        UUID hubId,
        UUID productId,
        int initialQuantity,
        UUID actorId,
        boolean actorIsMaster
) {
}
