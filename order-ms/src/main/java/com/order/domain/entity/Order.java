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
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID requesterCompanyId;    // 요청 업체 ID

    @Column(nullable = false)
    private UUID supplierCompanyId;     // 공급 업체 ID

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPriceSnapshot;   // 주문 당시 단가

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(unique = true)
    private UUID deliveryId;          // 배송 생성 전엔 null

    @Column(columnDefinition = "TEXT")
    private String requestNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus orderStatus;

    @Column(nullable = false)
    private UUID orderedBy;         // 주문자 ID

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

}
