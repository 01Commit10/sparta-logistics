package com.hub.inventory.infrastructure.persistence;

import com.hub.inventory.domain.entity.InventoryOperation;
import com.hub.inventory.domain.repository.InventoryOperationRepository;
import org.springframework.stereotype.Repository;
import org.hibernate.exception.ConstraintViolationException;
import com.hub.inventory.domain.exception.DuplicateInventoryRestoreException;
import com.hub.inventory.domain.exception.DuplicateInventoryOperationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

@Repository
public class InventoryOperationRepositoryImpl implements InventoryOperationRepository {

    private final InventoryOperationJpaRepository inventoryOperationJpaRepository;
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String IDEMPOTENCY_KEY_UNIQUE_CONSTRAINT =
            "uk_inventory_operations_idempotency_key";
    private static final String ORIGINAL_OPERATION_UNIQUE_CONSTRAINT =
            "uk_inventory_operations_original_operation_id";

    public InventoryOperationRepositoryImpl(InventoryOperationJpaRepository inventoryOperationJpaRepository) {
        this.inventoryOperationJpaRepository = inventoryOperationJpaRepository;
    }


    // 멱등성 키·원본 차감 ID의 유니크 제약 위반을 각각 내부 중복 예외로 변환한다.
    @Override
    public InventoryOperation save(InventoryOperation inventoryOperation) {
        try {
            return inventoryOperationJpaRepository.saveAndFlush(
                    inventoryOperation
            );
        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(
                    e,
                    IDEMPOTENCY_KEY_UNIQUE_CONSTRAINT
            )) {
                throw new DuplicateInventoryOperationException(e);
            }

            if (isUniqueConstraintViolation(
                    e,
                    ORIGINAL_OPERATION_UNIQUE_CONSTRAINT
            )) {
                throw new DuplicateInventoryRestoreException(e);
            }

            throw e;
        }
    }

    @Override
    public Optional<InventoryOperation> findByIdempotencyKey(String idempotencyKey) {
        return inventoryOperationJpaRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public Optional<InventoryOperation> findById(UUID operationId){
        return inventoryOperationJpaRepository.findById(operationId);
    }

    @Override
    public boolean existsByOriginalOperationId(UUID originalOperationId) {
        return inventoryOperationJpaRepository.existsByOriginalOperationId(
                originalOperationId
        );
    }

    // 예외 원인을 따라가며, 지정한 유니크 제약 위반인지 확인한다.
    private static boolean isUniqueConstraintViolation(
            Throwable exception,
            String constraintName
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && UNIQUE_VIOLATION_SQL_STATE.equals(
                    violation.getSQLState()
            )
                    && constraintName.equals(
                    violation.getConstraintName()
            )) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }


}
