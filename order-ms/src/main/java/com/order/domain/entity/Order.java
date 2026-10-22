package com.order.domain.entity;

import com.common.entity.BaseEntity;
import com.common.exception.BusinessException;
import com.common.exception.OrderErrorCode;
import com.order.domain.vo.OrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "p_orders")
@Check(name = "ck_orders_quantity", constraints = "quantity > 0")
@Check(name = "ck_orders_unit_price_snapshot", constraints = "unit_price_snapshot >= 0")
@Check(name = "ck_orders_total_amount", constraints = "total_amount >= 0")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID requesterCompanyId;

    @Column(nullable = false)
    private UUID supplierCompanyId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(unique = true)
    private UUID deliveryId;

    @Column(columnDefinition = "TEXT")
    private String requestNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus orderStatus;

    @Column(nullable = false)
    private UUID orderedBy;

    public static Order create(UUID requesterCompanyId, UUID supplierCompanyId,
                               UUID productId, int quantity, BigDecimal unitPrice,
                               String requestNote, UUID orderedBy) {
        validateQuantity(quantity);
        validateUnitPrice(unitPrice);

        Order order = new Order();
        order.requesterCompanyId = requesterCompanyId;
        order.supplierCompanyId = supplierCompanyId;
        order.productId = productId;
        order.quantity = quantity;
        order.unitPriceSnapshot = unitPrice;
        order.totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        order.requestNote = requestNote;
        order.orderedBy = orderedBy;
        order.orderStatus = OrderStatus.PROCESSING;
        return order;
    }

    private static void validateQuantity(int quantity) {
        if (quantity < 1) {
            throw new BusinessException(OrderErrorCode.INVALID_QUANTITY);
        }
    }

    private static void validateUnitPrice(BigDecimal unitPrice) {
        if (unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(OrderErrorCode.INVALID_UNIT_PRICE);
        }
    }

    /**
     * 재고 차감 + 배송 생성 성공 시 주문 완료
     */
    public void complete(UUID deliveryId) {
        validateProcessing();
        this.deliveryId = deliveryId;
        this.orderStatus = OrderStatus.COMPLETED;
    }

    /**
     * 재고 부족, 배송 생성 실패 등으로 주문 생성 실패 (처리 중인 주문만 실패 처리)
     */
    public void fail() {
        validateProcessing();
        this.orderStatus = OrderStatus.FAILED;
    }

    private void validateProcessing() {
        if (orderStatus != OrderStatus.PROCESSING) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_PROCESSING);
        }
    }

    /**
     * 이 주문을 주문한 사용자인지 확인 (조회, 삭제 권한 판단에 사용)
     */
    public boolean isOrderedBy(UUID userId) {
        return this.orderedBy.equals(userId);
    }

    // 재고 차감 작업 ID (취소 시 재고 복원에 사용)
    private UUID stockOperationId;

    // 기존 complete(UUID deliveryId)를 교체
    public void complete(UUID deliveryId, UUID stockOperationId) {
        validateProcessing();
        this.deliveryId = deliveryId;
        this.stockOperationId = stockOperationId;
        this.orderStatus = OrderStatus.COMPLETED;
    }

    // 외부 호출 전에 먼저 확인하려고 따로 연다
    public void validateCancellable() {
        if (orderStatus != OrderStatus.COMPLETED || stockOperationId == null) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_CANCELLABLE);
        }
    }

    public void cancel() {
        validateCancellable();
        this.orderStatus = OrderStatus.CANCELED;
    }

    /**
     * 논리 삭제. BaseEntity.softDelete가 deleted_at, deleted_by를 기록한다.
     */
    public void delete(UUID actorId) {
        if (orderStatus != OrderStatus.CANCELED && orderStatus != OrderStatus.FAILED) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_DELETABLE);
        }
        softDelete(actorId);
    }
}
