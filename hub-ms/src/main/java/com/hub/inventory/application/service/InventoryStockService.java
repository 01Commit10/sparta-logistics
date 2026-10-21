package com.hub.inventory.application.service;

import com.hub.inventory.application.dto.*;
import java.util.UUID;

/** Both modes expose the same API values; their durability completion points differ. */
public interface InventoryStockService {
    DecreaseInventoryResult decrease(DecreaseInventoryCommand command, UUID actorId);
    RestoreInventoryResult restore(RestoreInventoryCommand command, UUID actorId);
}
