package com.order.domain.entity;

import com.common.exception.BusinessException;
import com.common.exception.OrderErrorCode;
import com.order.domain.vo.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

public class OrderTest {

    private static final BigDecimal UNIT_PRICE = new BigDecimal("1000.00");

    private Order createOrder(int quantity, BigDecimal unitPrice) {
        return Order.create(
                UUID.randomUUID(),  // requesterCompanyId
                UUID.randomUUID(),  // supplierCompanyId
                UUID.randomUUID(),  // productId
                quantity,
                unitPrice,
                "요청사항",
                UUID.randomUUID()
        );
    }

    @Test
    @DisplayName("주문을 생성하면 총액은 단가 × 수량이고, 상태는 CREATED이다")
    void create_order() {
        Order order = createOrder(3, UNIT_PRICE);

        assertThat(order.getTotalAmount()).isEqualByComparingTo("3000.00");
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getQuantity()).isEqualTo(3);
    }

    @ParameterizedTest(name = "수량이 {0}이면 예외가 발생한다")
    @ValueSource(ints = {0, -1})
    @DisplayName("수량이 0 이하면 주문을 생성하지 않는다")
    void create_order_fail_when_quantity_is_not_positive(int quantity) {
        assertThatThrownBy(() -> createOrder(quantity, UNIT_PRICE))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(OrderErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("수량이 1이면 주문을 생성할 수 있다 (경계값)")
    void create_order_with_minimum_quantity() {
        Order order = createOrder(1, UNIT_PRICE);

        assertThat(order.getQuantity()).isEqualTo(1);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("1000.00");
    }


}
