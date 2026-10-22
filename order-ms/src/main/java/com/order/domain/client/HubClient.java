package com.order.domain.client;

import java.util.List;
import java.util.UUID;

public interface HubClient {
    List<InventoryInfo> findInventories(UUID productId);

    StockOperationResult decreaseStock(StockDecreaseCommand command);

    StockOperationResult restoreStock(StockRestoreCommand command);
}
