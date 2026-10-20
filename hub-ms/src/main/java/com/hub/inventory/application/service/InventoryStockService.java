package com.hub.inventory.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.hub.inventory.application.dto.DecreaseInventoryCommand;
import com.hub.inventory.application.dto.DecreaseInventoryResult;
import com.hub.inventory.domain.dto.DecreaseResult;
import com.hub.inventory.domain.entity.InventoryHistory;
import com.hub.inventory.domain.entity.InventoryOperation;
import com.hub.inventory.domain.repository.InventoryHistoryRepository;
import com.hub.inventory.domain.repository.InventoryOperationRepository;
import com.hub.inventory.domain.repository.InventoryStockRepository;
import com.hub.inventory.domain.vo.InventoryRejectionReason;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import com.hub.inventory.domain.vo.InventoryOperationStatus;
import com.common.exception.InventoryErrorCode;
import org.springframework.transaction.support.TransactionTemplate;
import com.hub.inventory.domain.exception.DuplicateInventoryOperationException;
import com.hub.inventory.domain.exception.DuplicateInventoryRestoreException;
import com.hub.inventory.application.dto.RestoreInventoryCommand;
import com.hub.inventory.application.dto.RestoreInventoryResult;
import com.hub.inventory.domain.dto.RestoreResult;
import com.hub.inventory.domain.vo.InventoryOperationType;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class InventoryStockService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 120;

    private final TransactionTemplate transactionTemplate;
    private final InventoryStockRepository inventoryStockRepository;
    private final InventoryOperationRepository inventoryOperationRepository;
    private final InventoryHistoryRepository inventoryHistoryRepository;

    public InventoryStockService(TransactionTemplate transactionTemplate, InventoryStockRepository inventoryStockRepository, InventoryOperationRepository inventoryOperationRepository, InventoryHistoryRepository inventoryHistoryRepository) {
        this.transactionTemplate = transactionTemplate;
        this.inventoryStockRepository = inventoryStockRepository;
        this.inventoryOperationRepository = inventoryOperationRepository;
        this.inventoryHistoryRepository = inventoryHistoryRepository;
    }

    /**
     * 요청을 검증하고, 재고 처리 트랜잭션의 커밋이 완료된 결과를 반환한다.
     *
     * 호출자의 트랜잭션이 있으면 일시 중단하고, TransactionTemplate으로 별도 트랜잭션을 실행한다.
     * 재고 부족·대상 없음은 결과로 반환해 거절 기록을 커밋하고, 컨트롤러에서 404·409로 변환한다.
     * 처리 중 예외가 발생하면 execute()가 롤백한 뒤 예외를 호출자에게 전달한다.
     *
     * 같은 키의 동시 요청으로 Operation 저장 중 중복 예외가 나면 처리 트랜잭션은 롤백된다.
     * 이후 새 트랜잭션에서 먼저 저장된 작업을 조회해, 요청 내용이 같으면 그 결과를 반환하고 다르면 IDEMPOTENCY_CONFLICT를 던진다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DecreaseInventoryResult decrease(
            DecreaseInventoryCommand command,
            UUID actorId
    ) {
        validateCommand(command);

        try {
            return transactionTemplate.execute(status ->
                    decreaseInTransaction(command, actorId)
            );
        } catch (DuplicateInventoryOperationException e) {
            return transactionTemplate.execute(status ->
                    findExistingResult(command, e)
            );
        }
    }

    /**
     * TransactionTemplate이 시작한 트랜잭션 안에서 재고 작업을 처리한다.
     *
     * 1. 같은 키의 기록이 있으면 요청 내용을 비교해 기존 결과를 반환하거나 충돌로 거절한다.
     * 2. 기록이 없으면 재고 차감을 시도하고, 결과에 맞는 Operation을 생성한다.
     *    ├─ 대상 없음 → REJECTED + INVENTORY_NOT_FOUND
     *    ├─ 수량 부족 → REJECTED + INSUFFICIENT_STOCK
     *    └─ 차감 성공 → SUCCEEDED
     * 3. Operation을 저장하고, 실제 차감이 성공한 경우에만 History를 저장한다.
     * 4. 저장된 Operation을 응용 DTO로 변환한다. 커밋은 이 메서드 반환 후 execute()에서 진행한다.
     */
    private DecreaseInventoryResult decreaseInTransaction(
            DecreaseInventoryCommand command,
            UUID actorId
    ) {

        Optional<InventoryOperation> existingOperation =
                inventoryOperationRepository.findByIdempotencyKey(command.idempotencyKey());

        if (existingOperation.isPresent()) {
            InventoryOperation previousOperation = existingOperation.get();

            if (!previousOperation.matchesDecreaseRequest(
                    command.orderId(),
                    command.hubId(),
                    command.productId(),
                    command.quantity()
            )) {
                throw new BusinessException(
                        InventoryErrorCode.IDEMPOTENCY_CONFLICT
                );
            }

            return DecreaseInventoryResult.fromEntity(previousOperation);
        }

        Optional<DecreaseResult> decreaseResult =
                inventoryStockRepository.decrease(
                    command.hubId(),
                    command.productId(),
                    command.quantity(),
                    actorId,
                    LocalDateTime.now()
                );

        InventoryOperation operation;

        if (decreaseResult.isEmpty()) {
            operation = InventoryOperation.rejectedDecrease(
                    command.idempotencyKey(),
                    command.orderId(),
                    command.hubId(),
                    command.productId(),
                    command.quantity(),
                    null,
                    null,
                    InventoryRejectionReason.INVENTORY_NOT_FOUND
                 );
             } else
             {
                DecreaseResult stock = decreaseResult.get();

                if (stock.decreased()) {
                     operation = InventoryOperation.succeededDecrease(
                            command.idempotencyKey(),
                            command.orderId(),
                            command.hubId(),
                            command.productId(),
                            command.quantity(),
                            stock.inventoryId(),
                            stock.remainingQuantity()
                      );
                    } else {
                     operation = InventoryOperation.rejectedDecrease(
                            command.idempotencyKey(),
                            command.orderId(),
                            command.hubId(),
                            command.productId(),
                            command.quantity(),
                            stock.inventoryId(),
                            stock.remainingQuantity(),
                            InventoryRejectionReason.INSUFFICIENT_STOCK
                        );
                    }
            }

        InventoryOperation savedOperation =
                inventoryOperationRepository.save(operation);

        boolean succeeded =
                savedOperation.getOperationStatus()
                        == InventoryOperationStatus.SUCCEEDED;

        if (succeeded) {
            InventoryHistory history = InventoryHistory.decrease(
                    savedOperation.getId(),
                    savedOperation.getInventoryId(),
                    savedOperation.getOrderId(),
                    savedOperation.getRequestedQuantity(),
                    savedOperation.getRemainingQuantity()
            );
            inventoryHistoryRepository.save(history);
        }
        return DecreaseInventoryResult.fromEntity(savedOperation);
    }

    // 복원을 처리하고, 중복 충돌이나 재고 변경 거절 시 롤백 후 기존 처리 결과를 확인한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RestoreInventoryResult restore(
            RestoreInventoryCommand command,
            UUID actorId
    ) {
        validateRestoreCommand(command);

        try {
            return transactionTemplate.execute(status ->
                    restoreInTransaction(command, actorId)
            );
        } catch (DuplicateInventoryOperationException e) {
            return transactionTemplate.execute(status ->
                    findExistingRestoreResult(command, e)
            );
        } catch (DuplicateInventoryRestoreException e) {
            // 같은 키의 동시 요청도 원본 차감 제약으로 충돌할 수 있어 키로 먼저 조회하고,
            // 같은 키의 기록이 없을 때만 다른 키로 이미 복원된 원본으로 거절한다.
            return transactionTemplate.execute(status ->
                    findExistingRestoreResult(
                            command,
                            new BusinessException(
                                    InventoryErrorCode.INVENTORY_ALREADY_RESTORED
                            )
                    )
            );
        } catch (BusinessException e) {
            if (e.getErrorCode() != InventoryErrorCode.INVENTORY_NOT_FOUND
                    && e.getErrorCode()
                        != InventoryErrorCode.INVENTORY_QUANTITY_LIMIT_EXCEEDED) {
                throw e;
            }

            // 재고 변경이 먼저 거절돼도, 동시에 완료된 복원이 있다면 그 결과를 우선한다.
            return transactionTemplate.execute(status ->
                    findExistingRestoreResult(command, e)
            );
        }
    }


    // 원본 차감을 기준으로 재고를 복원하고, 복원 작업과 변경 이력을 저장.
    private RestoreInventoryResult restoreInTransaction(
            RestoreInventoryCommand command,
            UUID actorId
    ) {
        Optional<InventoryOperation> existingOperation =
                inventoryOperationRepository.findByIdempotencyKey(
                        command.idempotencyKey()
                );

        if (existingOperation.isPresent()) {
            InventoryOperation previousOperation = existingOperation.get();

            if (!previousOperation.matchesRestoreRequest(
                    command.originalOperationId()
            )) {
                throw new BusinessException(
                        InventoryErrorCode.IDEMPOTENCY_CONFLICT
                );
            }

            return RestoreInventoryResult.fromEntity(previousOperation);
        }

        InventoryOperation originalOperation =
                inventoryOperationRepository.findById(
                        command.originalOperationId()
                ).orElseThrow(() -> new BusinessException(
                        CommonErrorCode.RESOURCE_NOT_FOUND
                ));

        // 실제 차감에 성공한 작업만 복원할 수 있다.
        if (originalOperation.getOperationType()
                != InventoryOperationType.DECREASE
                || originalOperation.getOperationStatus()
                != InventoryOperationStatus.SUCCEEDED) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        RestoreResult stock = inventoryStockRepository.restore(
                originalOperation.getInventoryId(),
                originalOperation.getRequestedQuantity(),
                actorId,
                LocalDateTime.now()
        ).orElseThrow(() -> new BusinessException(
                InventoryErrorCode.INVENTORY_NOT_FOUND
        ));

        InventoryOperation operation = InventoryOperation.succeededRestore(
                command.idempotencyKey(),
                originalOperation,
                stock.remainingQuantity()
        );

        InventoryOperation savedOperation =
                inventoryOperationRepository.save(operation);

        InventoryHistory history = InventoryHistory.restore(
                savedOperation.getId(),
                savedOperation.getInventoryId(),
                savedOperation.getOrderId(),
                savedOperation.getRequestedQuantity(),
                savedOperation.getRemainingQuantity()
        );

        inventoryHistoryRepository.save(history);

        return RestoreInventoryResult.fromEntity(savedOperation);
    }

    // 중복 충돌 후 기존 작업을 재조회하고, 요청 내용이 같으면 저장된 결과를 반환한다.
    private DecreaseInventoryResult findExistingResult(
            DecreaseInventoryCommand command,
            DuplicateInventoryOperationException duplicateException
    ) {
        InventoryOperation existingOperation =
                inventoryOperationRepository
                        .findByIdempotencyKey(command.idempotencyKey())
                        .orElseThrow(() -> duplicateException);

        if (!existingOperation.matchesDecreaseRequest(
                command.orderId(),
                command.hubId(),
                command.productId(),
                command.quantity()
        )) {
            throw new BusinessException(
                    InventoryErrorCode.IDEMPOTENCY_CONFLICT
            );
        }

        return DecreaseInventoryResult.fromEntity(existingOperation);
    }


    // 같은 키의 결과를 우선 확인하고, 다른 키의 기존 복원도 없을 때만 원래 예외를 전달한다.
    private RestoreInventoryResult findExistingRestoreResult(
            RestoreInventoryCommand command,
            RuntimeException keyNotFoundException
    ) {
        Optional<InventoryOperation> existingOperation =
                inventoryOperationRepository.findByIdempotencyKey(
                        command.idempotencyKey()
                );

        if (existingOperation.isEmpty()) {
            if (inventoryOperationRepository.existsByOriginalOperationId(
                    command.originalOperationId()
            )) {
                throw new BusinessException(
                        InventoryErrorCode.INVENTORY_ALREADY_RESTORED
                );
            }
            throw keyNotFoundException;
        }

        InventoryOperation previousOperation = existingOperation.get();

        if (!previousOperation.matchesRestoreRequest(
                command.originalOperationId()
        )) {
            throw new BusinessException(
                    InventoryErrorCode.IDEMPOTENCY_CONFLICT
            );
        }

        return RestoreInventoryResult.fromEntity(previousOperation);
    }

    // 차감 요청의 필수 식별자·양수 수량·멱등성 키 형식을 검증한다.
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

    // 복원 요청의 원본 차감 ID와 멱등성 키가 올바르게 전달됐는지 검증.
    private void validateRestoreCommand(RestoreInventoryCommand command) {
        if (command == null
                || command.originalOperationId() == null
                || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length()
                > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
