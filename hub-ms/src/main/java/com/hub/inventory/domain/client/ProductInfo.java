package com.hub.inventory.domain.client;

import java.util.UUID;

public record ProductInfo(
        UUID productId,
        UUID companyId,
        UUID hubId,
        UUID managerUserId
) {
}
