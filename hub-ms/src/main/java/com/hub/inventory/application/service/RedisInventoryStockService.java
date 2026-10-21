package com.hub.inventory.application.service;

import com.common.exception.*;
import com.hub.inventory.application.dto.*;
import com.hub.inventory.domain.stream.*;
import com.hub.inventory.domain.vo.InventoryOperationType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@ConditionalOnProperty(name="inventory.stock.mode",havingValue="redis-streams")
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class RedisInventoryStockService implements InventoryStockService {
    private final InventoryPipelineService pipeline;
    private final InventoryRequestAudit audit;
    public RedisInventoryStockService(InventoryPipelineService pipeline,InventoryRequestAudit audit) { this.pipeline=pipeline; this.audit=audit; }
    @Override public DecreaseInventoryResult decrease(DecreaseInventoryCommand command,UUID actorId) {
        if(command==null || command.orderId()==null || command.hubId()==null || command.productId()==null || command.quantity()<=0)
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        validateKey(command.idempotencyKey());
        var stamp=audit.capture();
        var request=new ReservationRequest(UUID.randomUUID(),command.idempotencyKey(),InventoryOperationType.DECREASE,null,
                command.orderId(),command.hubId(),command.productId(),command.quantity(),actorId,now(),stamp.createdAt(),stamp.createdBy(),stamp.updatedAt(),stamp.updatedBy());
        try {
            var e=pipeline.reserve(request);
            return new DecreaseInventoryResult(e.operationId(),e.orderId(),e.operationStatus(),e.inventoryId(),e.stockChange(),e.remainingQuantity(),e.rejectionReason());
        } catch(InventoryPipelineException e) { throw InventoryPipelineErrors.translate(e); }
    }
    @Override public RestoreInventoryResult restore(RestoreInventoryCommand command,UUID actorId) {
        if(command==null || command.originalOperationId()==null) throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        validateKey(command.idempotencyKey());
        var stamp=audit.capture();
        var request=new ReservationRequest(UUID.randomUUID(),command.idempotencyKey(),InventoryOperationType.RESTORE,command.originalOperationId(),
                null,null,null,0,actorId,now(),stamp.createdAt(),stamp.createdBy(),stamp.updatedAt(),stamp.updatedBy());
        try {
            var e=pipeline.reserve(request);
            return new RestoreInventoryResult(e.operationId(),e.orderId(),e.operationStatus(),e.inventoryId(),e.stockChange(),e.remainingQuantity());
        } catch(InventoryPipelineException e) { throw InventoryPipelineErrors.translate(e); }
    }
    private static LocalDateTime now() { return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS); }
    private static void validateKey(String key) {
        if(key==null || key.isBlank() || key.length()>120) throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }
}
