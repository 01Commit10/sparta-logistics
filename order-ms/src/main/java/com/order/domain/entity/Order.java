package com.order.domain.entity;

import com.common.entity.BaseEntity;
import com.common.exception.BusinessException;
import com.common.exception.OrderErrorCode;
import com.order.domain.vo.OrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "p_orders")
/*@Check(name = "ck_orders_quantity", constraints = "quantity > 0")
@Check(name = "ck_orders_unit_price_snapshot", constraints = "unit_price_snapshot >= 0")
@Check(name = "ck_orders_total_amount", constraints = "total_amount >= 0")*/
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
/*
    *//** 재고 차감 + 배송 생성 성공 시 *//*
    public void complete(UUID deliveryId) {
        validateProcessing();
        this.deliveryId = deliveryId;
        this.orderStatus = OrderStatus.COMPLETED;
    }

    *//** 재고 부족, 배송 생성 실패 등으로 주문 생성 실패 시 *//*
    public void fail() {
        validateProcessing();
        this.orderStatus = OrderStatus.FAILED;
    }

    private void validateProcessing() {
        if (orderStatus != OrderStatus.PROCESSING) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_PROCESSING);
        }
    }*/

}
