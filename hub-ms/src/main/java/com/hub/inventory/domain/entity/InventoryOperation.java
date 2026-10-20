package com.hub.inventory.domain.entity;

import com.common.entity.BaseEntity;
import com.hub.inventory.domain.vo.InventoryOperationStatus;
import com.hub.inventory.domain.vo.InventoryOperationType;
import com.hub.inventory.domain.vo.InventoryRejectionReason;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;

import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name="p_inventory_operations",
        schema ="hub",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_inventory_operations_idempotency_key",
                        columnNames = "idempotency_key"
                ),
                @UniqueConstraint(
                        name = "uk_inventory_operations_original_operation_id",
                        columnNames = "original_operation_id"
                )
        }
)
public class InventoryOperation extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 120;

    @Column(
            name = "idempotency_key",
            nullable = false,
            updatable = false,
            length = MAX_IDEMPOTENCY_KEY_LENGTH
    )
    private String idempotencyKey;

    @Column(name = "original_operation_id", updatable = false)
    private UUID originalOperationId;

    @Column(name="order_id", updatable = false)
    private UUID orderId;

    @Column(name="hub_id", nullable = false, updatable = false)
    private UUID hubId;

    @Column(name="product_id", nullable = false, updatable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "operation_type",
            nullable = false,
            updatable = false,
            length = 20
    )
    private InventoryOperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(
            name="operation_status",
            nullable = false,
            updatable = false,
            length = 20
    )
    private InventoryOperationStatus operationStatus;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "rejection_reason",
            updatable = false,
            length = 40
    )
    private InventoryRejectionReason rejectionReason;

    @Column(name="requested_quantity", nullable = false, updatable = false)
    private int requestedQuantity;

    @Column(name="inventory_id", updatable = false)
    private UUID inventoryId;

    @Column(name = "remaining_quantity", updatable = false)
    private Integer remainingQuantity;

    // 현재 Operation에 기록된 작업이 차감이며, 주문 ID·허브 ID·상품 ID·요청 수량이 새 요청과 모두 같은지 확인
    public boolean matchesDecreaseRequest(
            UUID orderId,
            UUID hubId,
            UUID productId,
            int quantity
    ) {
        return this.operationType == InventoryOperationType.DECREASE
                && Objects.equals(this.orderId, orderId)
                && Objects.equals(this.hubId, hubId)
                && Objects.equals(this.productId, productId)
                && this.requestedQuantity == quantity;
    }

    // 같은 복원 요청을 다시 보낸 것인지 확인
    public boolean matchesRestoreRequest(UUID originalOperationId) {
        return this.operationType == InventoryOperationType.RESTORE
                && Objects.equals( this.originalOperationId, originalOperationId );
    }

    //정적 팩토리 메소드:  필드를 내부에서 조합하는 등 유효한 객체생성을 보장
    //차감 성공기록  정적팩토리 메소드
    public static InventoryOperation succeededDecrease(
            String idempotencyKey,
            UUID orderId,
            UUID hubId,
            UUID productId,
            int quantity,
            UUID inventoryId,
            int remainingQuantity
    ){
        return createDecrease(
                InventoryOperationStatus.SUCCEEDED,
                idempotencyKey,
                orderId,
                hubId,
                productId,
                quantity,
                inventoryId,
                remainingQuantity,
                null
        );
    }

    //수량부족 거절 기록 정적팩토리 메소드
    public static InventoryOperation rejectedDecrease(
            String idempotencyKey,
            UUID orderId,
            UUID hubId,
            UUID productId,
            int quantity,
            UUID inventoryId,
            Integer remainingQuantity, // int는  null 불가능 integer null 가능
            InventoryRejectionReason rejectionReason
    ){
        return createDecrease(
                InventoryOperationStatus.REJECTED,
                idempotencyKey,
                orderId,
                hubId,
                productId,
                quantity,
                inventoryId,
                remainingQuantity,
                rejectionReason
        );
    }

    // 성공한 원본 차감 기록을 기준으로 복원 성공 Operation을 생성
    public static InventoryOperation succeededRestore(
            String idempotencyKey,
            InventoryOperation originalOperation,
            int remainingQuantity
    ) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH
                || originalOperation == null
                || remainingQuantity < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        if (originalOperation.getId() == null
                || originalOperation.getOperationType()
                != InventoryOperationType.DECREASE
                || originalOperation.getOperationStatus()
                != InventoryOperationStatus.SUCCEEDED
                || originalOperation.getOrderId() == null
                || originalOperation.getHubId() == null
                || originalOperation.getProductId() == null
                || originalOperation.getInventoryId() == null
                || originalOperation.getRequestedQuantity() <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        InventoryOperation operation = new InventoryOperation();
        operation.idempotencyKey = idempotencyKey;
        operation.originalOperationId = originalOperation.getId();
        operation.orderId = originalOperation.getOrderId();
        operation.hubId = originalOperation.getHubId();
        operation.productId = originalOperation.getProductId();
        operation.inventoryId = originalOperation.getInventoryId();
        operation.operationType = InventoryOperationType.RESTORE;
        operation.operationStatus = InventoryOperationStatus.SUCCEEDED;
        operation.requestedQuantity = originalOperation.getRequestedQuantity();
        operation.remainingQuantity = remainingQuantity;

        return operation;
    }


    // 성공·거절 차감 기록의 필드 검증과 객체 생성 공통 메소드
    private static InventoryOperation createDecrease(
            InventoryOperationStatus status,
            String idempotencyKey,
            UUID orderId,
            UUID hubId,
            UUID productId,
            int quantity,
            UUID inventoryId,
            Integer remainingQuantity,
            InventoryRejectionReason rejectionReason
    ){
        if (status == null
                || idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH
                || orderId == null
                || hubId == null
                || productId == null
                || quantity <= 0
                || (remainingQuantity != null && remainingQuantity < 0)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        if ((status == InventoryOperationStatus.SUCCEEDED
                && rejectionReason != null)
                || (status == InventoryOperationStatus.REJECTED
                && rejectionReason == null)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        if (rejectionReason == InventoryRejectionReason.INVENTORY_NOT_FOUND) {
            if (inventoryId != null || remainingQuantity != null) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
        } else if (inventoryId == null || remainingQuantity == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        InventoryOperation operation = new InventoryOperation();
        operation.idempotencyKey = idempotencyKey;
        operation.orderId = orderId;
        operation.hubId = hubId;
        operation.productId = productId;
        operation.operationType = InventoryOperationType.DECREASE;
        operation.operationStatus = status;
        operation.rejectionReason = rejectionReason;
        operation.requestedQuantity = quantity;
        operation.inventoryId = inventoryId;
        operation.remainingQuantity = remainingQuantity;

        return operation;
    }
}

