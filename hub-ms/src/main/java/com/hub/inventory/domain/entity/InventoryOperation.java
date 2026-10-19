package com.hub.inventory.domain.entity;

import com.common.entity.BaseEntity;
import com.hub.inventory.domain.vo.InventoryOperationStatus;
import com.hub.inventory.domain.vo.InventoryOperationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;

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

    @Column(name="requested_quantity", nullable = false, updatable = false)
    private int requestedQuantity;

    @Column(name="inventory_id", updatable = false)
    private UUID inventoryId;

    @Column(name = "remaining_quantity", updatable = false)
    private Integer remainingQuantity;

    //정적 팩토리 메소드: type, status 특정 필드를 내부에서 조합하는 등 유효한 객체생성을 보장하려고
    public static InventoryOperation succeededDecrease(
            String idempotencyKey,
            UUID orderId,
            UUID hubId,
            UUID productId,
            int quantity,
            UUID inventoryId,
            int remainingQuantity
    ){
        //들어오는 값이 하나라도 적절하지 않으면 inventoryOperation 생성 불가.
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH
                || orderId == null
                || hubId == null
                || productId == null
                || inventoryId == null
                || quantity <= 0
                || remainingQuantity < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        InventoryOperation operation = new InventoryOperation();
        operation.idempotencyKey = idempotencyKey;
        operation.orderId = orderId;
        operation.hubId = hubId;
        operation.productId = productId;
        operation.operationType = InventoryOperationType.DECREASE;
        operation.operationStatus = InventoryOperationStatus.SUCCEEDED;
        operation.requestedQuantity = quantity;
        operation.inventoryId = inventoryId;
        operation.remainingQuantity = remainingQuantity;

        return operation;
    }
}

