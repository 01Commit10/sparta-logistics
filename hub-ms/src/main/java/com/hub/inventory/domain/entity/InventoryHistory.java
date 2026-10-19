package com.hub.inventory.domain.entity;

import com.common.entity.BaseEntity;
import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.hub.inventory.domain.vo.InventoryOperationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "p_inventory_histories",
        schema = "hub",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_inventory_histories_operation_id",
                        columnNames = "operation_id"
                )
        }
)
@Check(
        name = "ck_inventory_histories_stock",
        constraints = "stock_change <> 0 AND stock_after >= 0"
)
public class InventoryHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @Column(name = "inventory_id", nullable = false, updatable = false)
    private UUID inventoryId;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "change_type",
            nullable = false,
            updatable = false,
            length = 20
    )
    private InventoryOperationType changeType;

    @Column(name = "stock_change", nullable = false, updatable = false)
    private int stockChange;

    @Column(name = "stock_after", nullable = false, updatable = false)
    private int stockAfter;

    // 실제 차감 결과를 바탕으로 재고 변경 이력 객체를 생성한다.
    public static InventoryHistory decrease(
            UUID operationId,
            UUID inventoryId,
            UUID orderId,
            int quantity,
            int stockAfter
    ) {
        if (operationId == null
                || inventoryId == null
                || orderId == null
                || quantity <= 0
                || stockAfter < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        InventoryHistory history = new InventoryHistory();
        history.operationId = operationId;
        history.inventoryId = inventoryId;
        history.orderId = orderId;
        history.changeType = InventoryOperationType.DECREASE;
        history.stockChange = -quantity;
        history.stockAfter = stockAfter;

        return history;
    }
}