package com.hub.inventory.domain.stream;
import com.hub.inventory.domain.vo.InventoryOperationType;
import java.time.LocalDateTime;
import java.util.UUID;
/** RESTORE only requires originalOperationId; its stock/request identifiers are resolved from the ledger. */
public record ReservationRequest(UUID operationId, String idempotencyKey,
        InventoryOperationType operationType, UUID originalOperationId, UUID orderId,
        UUID hubId, UUID productId, int quantity, UUID actorId,
        LocalDateTime occurredAt, LocalDateTime createdAt, UUID createdBy,
        LocalDateTime updatedAt, UUID updatedBy) { }
