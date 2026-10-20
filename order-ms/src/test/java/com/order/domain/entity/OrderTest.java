package com.order.domain.entity;

import com.common.exception.BusinessException;
import com.common.exception.OrderErrorCode;
import com.order.domain.vo.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    // 초기 상태만 확인하는 테스트
    @Test
    @DisplayName("주문을 생성하면 총액은 단가 × 수량이고, 상태는 PROCESSING 이다.")
    void createOrderSuccess() {
        Order order = createOrder(3, UNIT_PRICE);

        assertThat(order.getTotalAmount()).isEqualByComparingTo("3000.00");
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(order.getQuantity()).isEqualTo(3);
    }

    // 총액 계산 규칙 (경계값 포함 4케이스)
    @ParameterizedTest(name = "수량 {0}, 단가 {1} -> 총액 {2}")
    @CsvSource({
            "1, 1000.00, 1000.00",   // 수량 경계값
            "3, 1000.00, 3000.00",
            "3, 1500.50, 4501.50",   // 소수점 단가
            "3, 0,       0"          // 단가 경계값 (0원 허용)
    })
    @DisplayName("주문을 생성하면 총액은 단가 x 수량이다.")
    void createOrderTotalSuccess(int quantity, BigDecimal unitPrice, BigDecimal expectedTotal) {
        Order order = createOrder(quantity, unitPrice);

        assertThat(order.getTotalAmount()).isEqualByComparingTo(expectedTotal);
    }


    @ParameterizedTest(name = "주문 수량이 {0}이면 예외가 발생한다.")
    @ValueSource(ints = {0, -1})
    @DisplayName("주문 수량이 0 이하면 주문을 생성하지 않는다")
    void createOrderFailInvalidQuantity(int quantity) {
        assertThatThrownBy(() -> createOrder(quantity, UNIT_PRICE))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(OrderErrorCode.INVALID_QUANTITY);
    }

    @ParameterizedTest(name = "주문 단가가 {0}이면 예외가 발생한다.")
    @ValueSource(strings = {"-1", "-0.01"})
    @DisplayName("주문 단가가 0원 미만이면 주문을 생성할 수 없다.")
    void createOrderFailNegativeUnitPrice(BigDecimal unitPrice) {
        assertThatThrownBy(() -> createOrder(1, unitPrice))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(OrderErrorCode.INVALID_UNIT_PRICE);
    }

    @Test
    @DisplayName("처리 중인 주문을 완료하면 상태는 COMPLETED이고 배송 ID가 저장된다.")
    void completedOrderSuccess() {
        Order order = createOrder(3, UNIT_PRICE);
        UUID deliveryId = UUID.randomUUID();

        order.complete(deliveryId);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getDeliveryId()).isEqualTo(deliveryId);
    }

    @Test
    @DisplayName("처리 중인 주문을 실패 처리하면 상태는 FAILED이다.")
    void failOrderSuccess() {
        Order order = createOrder(3, UNIT_PRICE);

        order.fail(); // 처리 중인 주문만 실패 처리

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    @DisplayName("처리 중이 아닌 주문은 완료, 실패 처리를 할 수 없다.")
    void changeOrderStatusFailNotProcessing() {
        Order order = createOrder(3, UNIT_PRICE);
        order.complete(UUID.randomUUID());

        assertThatThrownBy(() -> order.complete(UUID.randomUUID()))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(OrderErrorCode.ORDER_NOT_PROCESSING);

        assertThatThrownBy(order::fail)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(OrderErrorCode.ORDER_NOT_PROCESSING);
    }

    @Test
    @DisplayName("주문한 사용자면 isOrderedBy가 true를 반환한다.")
    void isOrderedByOwner() {
        UUID orderedId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                3, new BigDecimal("1000"), null, orderedId);

        assertThat(order.isOrderedBy(orderedId)).isTrue();
    }

    @Test
    @DisplayName("다른 사용자면 isOrderedBy가 false를 반환한단.")
    void isOrderedByOther() {
        Order order = Order.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                3, new BigDecimal("1000"), null, UUID.randomUUID());

        assertThat(order.isOrderedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("완료된 주문을 취소하면 상태는 CANCELLED이다")
    void cancelOrder() {
        Order order = createOrder(3, UNIT_PRICE);
        order.complete(UUID.randomUUID(), UUID.randomUUID());

        order.cancel();

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("완료되지 않은 주문은 취소할 수 없다")
    void cancelOrderFailNotCompleted() {
        Order order = createOrder(3, UNIT_PRICE);   // PROCESSING

        assertThatThrownBy(order::cancel)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(OrderErrorCode.ORDER_NOT_CANCELLABLE);
    }

}
