package com.order.application.service;

import com.order.application.dto.CreateOrderCommand;
import com.order.application.dto.OrderResult;
import com.order.domain.client.*;
import com.order.domain.entity.Order;
import com.order.domain.entity.OrderHistory;
import com.order.domain.repository.OrderHistoryRepository;
import com.order.domain.repository.OrderRepository;
import com.order.domain.vo.OrderOperationStatus;
import com.order.domain.vo.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderHistoryRepository orderHistoryRepository;

    @Mock
    private HubClient hubClient;

    @Mock
    private DeliveryClient deliveryClient;

    @InjectMocks
    private OrderService orderService;

    private static final UUID REQUESTER_COMPANY_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_COMPANY_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID HUB_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String IDEMPOTENCY_KEY = "order-req-0001";

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID DELIVERY_ID = UUID.randomUUID();


    @Test
    @DisplayName("재고 조회, 차감, 배송 생성이 모두 성공하면 주문은 COMPLETED, 이력은 SUCCEEDED가 도니다")
    void createOrderSuccess() {
        // given
        // ① 이력 저장
        given(orderHistoryRepository.save(any(OrderHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // ② 재고 조회
        given(hubClient.findInventories(PRODUCT_ID))
                .willReturn(List.of(new InventoryInfo(UUID.randomUUID(), HUB_ID, PRODUCT_ID, 100)));

        // ③ 주문 저장
        given(orderRepository.save(any(Order.class)))
                .willAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    ReflectionTestUtils.setField(order, "id", ORDER_ID);  // DB가 id를 채워주는 것을 흉내
                    return order;
                });

        // ④ 재고 차감
        given(hubClient.decreaseStock(any(StockDecreaseCommand.class)))
                .willReturn(new StockOperationResult(UUID.randomUUID(), true, null, 97));

        // ⑤ 배송 생성
        given(deliveryClient.createDelivery(any(CreateDeliveryCommand.class)))
                .willReturn(new DeliveryResult(DELIVERY_ID));

        // when
        OrderResult result = orderService.createOrder(createCommand(3));

        // then: 주문 결과
        assertThat(result.orderId()).isEqualTo(ORDER_ID);
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(result.deliveryId()).isEqualTo(DELIVERY_ID);

        // then: 재고 차감, 배송 생성이 올바른 값으로 호출됐는지
        then(hubClient).should().decreaseStock(
                new StockDecreaseCommand(ORDER_ID, HUB_ID, PRODUCT_ID, 3));
        then(deliveryClient).should().createDelivery(
                new CreateDeliveryCommand(ORDER_ID, HUB_ID, SUPPLIER_COMPANY_ID, REQUESTER_COMPANY_ID, USER_ID));
        then(hubClient).should(never()).restoreStock(any());

        // then: 이력
        ArgumentCaptor<OrderHistory> historyCaptor = ArgumentCaptor.forClass(OrderHistory.class);
        then(orderHistoryRepository).should().save(historyCaptor.capture());

        OrderHistory history = historyCaptor.getValue();
        assertThat(history.getOperationStatus()).isEqualTo(OrderOperationStatus.SUCCEEDED);
        assertThat(history.getResponseCode()).isEqualTo(201);
        assertThat(history.getOrder().getId()).isEqualTo(ORDER_ID);

        // assert가 여러 개지만 전부 "정상 흐름이 끝까지 갔다"는 한 개념을 확인하므로 한 테스트로 묶음
    }

    // 테스트마다 반복되는 주문 요청 생성
    private CreateOrderCommand createCommand(int quantity) {
        return new CreateOrderCommand(
                REQUESTER_COMPANY_ID,
                SUPPLIER_COMPANY_ID,
                PRODUCT_ID,
                quantity,
                new BigDecimal("1000.00"),
                "요청사항",
                USER_ID,
                IDEMPOTENCY_KEY
        );
    }
}