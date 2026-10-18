package com.hub.inventory.domain.client;

import java.util.Optional;
import java.util.UUID;

public interface ProductClient {
    Optional<ProductInfo> findActiveProduct(UUID productId);
}
