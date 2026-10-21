package com.hub.inventory.application.service;
import com.common.exception.*;
import com.hub.inventory.domain.stream.InventoryPipelineException;
final class InventoryPipelineErrors {
    private InventoryPipelineErrors() { }
    static BusinessException translate(InventoryPipelineException e) {
        return new BusinessException(switch(e.reason()) {
            case IDEMPOTENCY_CONFLICT -> InventoryErrorCode.IDEMPOTENCY_CONFLICT;
            case ALREADY_RESTORED -> InventoryErrorCode.INVENTORY_ALREADY_RESTORED;
            case ORIGINAL_NOT_FOUND -> CommonErrorCode.RESOURCE_NOT_FOUND;
            case INVALID_ORIGINAL -> CommonErrorCode.INVALID_INPUT;
            case INVENTORY_NOT_FOUND -> InventoryErrorCode.INVENTORY_NOT_FOUND;
            case QUANTITY_LIMIT -> InventoryErrorCode.INVENTORY_QUANTITY_LIMIT_EXCEEDED;
            case UNAVAILABLE -> CommonErrorCode.SERVICE_UNAVAILABLE;
        });
    }
}
