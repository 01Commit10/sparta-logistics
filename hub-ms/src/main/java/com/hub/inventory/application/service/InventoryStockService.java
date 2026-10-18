package com.hub.inventory.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.hub.inventory.application.dto.DecreaseInventoryCommand;
import com.hub.inventory.application.dto.DecreaseInventoryResult;
import com.hub.inventory.domain.dto.DecreaseResult;
import com.hub.inventory.domain.entity.Inventory;
import com.hub.inventory.domain.entity.InventoryOperation;
import com.hub.inventory.domain.repository.InventoryOperationRepository;
import com.hub.inventory.domain.repository.InventoryStockRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

//재고차감과 operation저장 하나의 트랜잭션으로
@Service
public class InventoryStockService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 120;

    private final InventoryStockRepository inventoryStockRepository;
    private final InventoryOperationRepository inventoryOperationRepository;

    public InventoryStockService(InventoryStockRepository inventoryStockRepository, InventoryOperationRepository inventoryOperationRepository) {
        this.inventoryStockRepository = inventoryStockRepository;
        this.inventoryOperationRepository = inventoryOperationRepository;
    }

    @Transactional
    public Optional<DecreaseInventoryResult> decrease(
            DecreaseInventoryCommand command,
            UUID actorId
    ){
        validateCommand(command);

        Optional<DecreaseResult> decreaseResult =
                inventoryStockRepository.decrease(
                    command.hubId(),
                    command.productId(),
                    command.quantity(),
                    actorId,
                    LocalDateTime.now()
                );
        if(decreaseResult.isEmpty()){
            return Optional.empty();
        }

        DecreaseResult stock = decreaseResult.get();

        InventoryOperation operation = InventoryOperation.succeededDecrease(
                command.idempotencyKey(),
                command.orderId(),
                command.hubId(),
                command.productId(),
                command.quantity(),
                stock.inventoryId(),
                stock.remainingQuantity()
        );

        InventoryOperation savedOperation =
                inventoryOperationRepository.save(operation);

        return Optional.of(new DecreaseInventoryResult(
                savedOperation.getId(),
                savedOperation.getOrderId(),
                savedOperation.getOperationStatus(),
                savedOperation.getInventoryId(),
                -savedOperation.getRequestedQuantity(),
                savedOperation.getRemainingQuantity()
        ));

    }

    // 재고 차감 입력정보 검증 메소드
    private void validateCommand(DecreaseInventoryCommand command) {
        if (command == null
                || command.orderId() == null
                || command.hubId() == null
                || command.productId() == null
                || command.quantity() <= 0
                || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length()
                > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

}
