package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.DeliveryErrorCode;
import com.common.exception.OrderErrorCode;
import com.order.application.dto.OrderResult;
import com.order.domain.client.DeliveryClient;
import com.order.domain.client.HubClient;
import com.order.domain.client.StockRestoreCommand;
import com.order.domain.entity.Order;
import com.order.domain.repository.OrderRepository;
import com.order.domain.vo.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class OrderCancelServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private DeliveryClient deliveryClient;
    @Mock
    private HubClient hubClient;

    @InjectMocks
    private OrderCancelService orderCancelService;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID DELIVERY_ID = UUID.randomUUID();
    private static final UUID STOCK_OPERATION_ID = UUID.randomUUID();

    private Order completedOrder() {
        Order order = Order.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                3, new BigDecimal("1000"), null, OWNER_ID);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.complete(DELIVERY_ID, STOCK_OPERATION_ID);
        return order;
    }

    @Test
    @DisplayName("배송 취소 → 재고 복원 순서로 처리하고 주문은 CANCELLED가 된다")
    void cancelOrder() {
        given(orderRepository.findActiveById(ORDER_ID)).willReturn(Optional.of(completedOrder()));

        OrderResult result = orderCancelService.cancelOrder(ORDER_ID, OWNER_ID, false);

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.CANCELED);
        InOrder inOrder = inOrder(deliveryClient, hubClient);
        inOrder.verify(deliveryClient).cancelDelivery(DELIVERY_ID);
        inOrder.verify(hubClient).restoreStock(new StockRestoreCommand(ORDER_ID, STOCK_OPERATION_ID));
    }

    @Test
    @DisplayName("배송이 출발했으면 409 예외가 발생하고 재고는 복원하지 않는다")
    void cancelOrderFailDeliveryDispatched() {
        Order order = completedOrder();
        given(orderRepository.findActiveById(ORDER_ID)).willReturn(Optional.of(order));
        willThrow(new BusinessException(DeliveryErrorCode.DELIVERY_NOT_CANCELABLE))
                .given(deliveryClient).cancelDelivery(DELIVERY_ID);

        assertThatThrownBy(() -> orderCancelService.cancelOrder(ORDER_ID, OWNER_ID, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DeliveryErrorCode.DELIVERY_NOT_CANCELABLE);

        then(hubClient).shouldHaveNoInteractions();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("완료되지 않은 주문은 외부 호출 없이 409 예외가 발생한다")
    void cancelOrderFailNotCompleted() {
        Order order = Order.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                3, new BigDecimal("1000"), null, OWNER_ID);   // PROCESSING
        given(orderRepository.findActiveById(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderCancelService.cancelOrder(ORDER_ID, OWNER_ID, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_CANCELLABLE);

        then(deliveryClient).shouldHaveNoInteractions();
        then(hubClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("주문자가 아닌 USER가 취소하면 403 예외가 발생한다")
    void cancelOrderFailOtherUser() {
        given(orderRepository.findActiveById(ORDER_ID)).willReturn(Optional.of(completedOrder()));

        assertThatThrownBy(() -> orderCancelService.cancelOrder(ORDER_ID, UUID.randomUUID(), false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.ACCESS_DENIED);

        then(deliveryClient).shouldHaveNoInteractions();
    }
}