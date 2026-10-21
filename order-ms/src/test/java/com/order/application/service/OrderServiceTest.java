package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.InventoryErrorCode;
import com.common.exception.OrderErrorCode;
import com.order.application.dto.CreateOrderCommand;
import com.order.application.dto.OrderResult;
import com.order.application.event.OrderCompletedEvent;
import com.order.domain.client.*;
import com.order.domain.entity.Order;
import com.order.domain.entity.OrderHistory;
import com.order.domain.repository.OrderHistoryRepository;
import com.order.domain.repository.OrderRepository;
import com.order.domain.vo.OrderOperationStatus;
import com.order.domain.vo.OrderOperationType;
import com.order.domain.vo.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

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

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
    private static final UUID DECREASE_OPERATION_ID = UUID.randomUUID();

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
                .willReturn(new StockOperationResult(DECREASE_OPERATION_ID, true, null, 97));

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
        then(hubClient).should(never()).restoreStock(new StockRestoreCommand(ORDER_ID, DECREASE_OPERATION_ID));

        // then: 이력
        ArgumentCaptor<OrderHistory> historyCaptor = ArgumentCaptor.forClass(OrderHistory.class);
        then(orderHistoryRepository).should().save(historyCaptor.capture());

        // then: Slack Event
        then(eventPublisher).should().publishEvent(any(OrderCompletedEvent.class));

        OrderHistory history = historyCaptor.getValue();
        assertThat(history.getOperationStatus()).isEqualTo(OrderOperationStatus.SUCCEEDED);
        assertThat(history.getResponseCode()).isEqualTo(201);
        assertThat(history.getOrder().getId()).isEqualTo(ORDER_ID);

        // assert가 여러 개지만 전부 "정상 흐름이 끝까지 갔다"는 한 개념을 확인하므로 한 테스트로 묶음
    }

    // 재요청재요청해도 재고가 두번 빠지지 않게 그대로 반환
    // 타임아웃으로 클라가 재시도 해도 재고가 두번 빠지지 않게 막는 기능
    // command.requestHash()로 꺼내둬서 테스트에서 service와 똑같은 해시로 이력 생성 가능
    @Test
    @DisplayName("같은 키로 이미 성공한 같은 요청이 다시 오면 재고·배송을 호출하지 않고 이전 결과를 반환한다")
    void createOrderReturnsPreviousResultWhenAlreadySucceeded() {
        // given: 이전에 성공적으로 처리된 주문과 이력
        CreateOrderCommand command = createCommand(3);

        Order order = Order.create(REQUESTER_COMPANY_ID, SUPPLIER_COMPANY_ID, PRODUCT_ID,
                3, new BigDecimal("1000.00"), "요청사항", USER_ID);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.complete(DELIVERY_ID);

        OrderHistory history = OrderHistory.create(OrderOperationType.CREATE, IDEMPOTENCY_KEY, command.requestHash());
        history.linkOrder(order);
        history.succeed(201);

        given(orderHistoryRepository.findByOperationTypeAndIdempotencyKey(OrderOperationType.CREATE, IDEMPOTENCY_KEY))
                .willReturn(Optional.of(history));

        // when
        OrderResult result = orderService.createOrder(command);

        // then: 이전 결과 그대로
        assertThat(result.orderId()).isEqualTo(ORDER_ID);
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(result.deliveryId()).isEqualTo(DELIVERY_ID);

        // then: 새로 처리한 것이 하나도 없어야 함
        then(hubClient).shouldHaveNoInteractions();
        then(deliveryClient).shouldHaveNoInteractions();
        then(orderRepository).shouldHaveNoInteractions();
        then(orderHistoryRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any(OrderCompletedEvent.class));
    }

    @ParameterizedTest
    @EnumSource(value = OrderOperationStatus.class, names = {"IN_PROGRESS", "FAILED"})
    @DisplayName("같은 키로 같은 요청이 왔지만 이전 요청이 진행 중이거나 실패했으면 409 예외가 발생한다")
    void createOrderFailsWhenPreviousRequestNotSucceeded(OrderOperationStatus previousStatus) {
        // given: 같은 내용의 이전 요청이 진행 중(IN_PROGRESS) 또는 실패(FAILED) 상태
        CreateOrderCommand command = createCommand(3);
        OrderHistory history = OrderHistory.create(OrderOperationType.CREATE, IDEMPOTENCY_KEY, command.requestHash());
        if (previousStatus == OrderOperationStatus.FAILED) {
            history.fail(503, "이전 요청 처리 실패");
        }

        given(orderHistoryRepository.findByOperationTypeAndIdempotencyKey(OrderOperationType.CREATE, IDEMPOTENCY_KEY))
                .willReturn(Optional.of(history));

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(command))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.PREVIOUS_REQUEST_NOT_SUCCEEDED);

        then(hubClient).shouldHaveNoInteractions();
        then(deliveryClient).shouldHaveNoInteractions();
        then(orderRepository).shouldHaveNoInteractions();
        then(orderHistoryRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any(OrderCompletedEvent.class));
    }

    @Test
    @DisplayName("재고 차감 중 결과를 모르면 같은 키로 다시 보내 차감 작업을 확인하고 복원한다.")
    void createOrderRestoresStockWhenDecreaseResultUnknown() {
        // given: 첫 차감은 타임아웃, 같은 키 재전송에서 hub가 이전 차감 결과를 돌려줌
        givenOrderSavedSuccessfully();
        given(hubClient.decreaseStock(any(StockDecreaseCommand.class)))
                .willThrow(new BusinessException(OrderErrorCode.INVENTORY_UNAVAILABLE))
                .willReturn(new StockOperationResult(DECREASE_OPERATION_ID, true, null, 97));

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(createCommand(3)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVENTORY_UNAVAILABLE);

        then(hubClient).should(times(2)).decreaseStock(new StockDecreaseCommand(ORDER_ID, HUB_ID, PRODUCT_ID, 3));
        then(hubClient).should().restoreStock(new StockRestoreCommand(ORDER_ID, DECREASE_OPERATION_ID));
        then(deliveryClient).shouldHaveNoInteractions();
        then(eventPublisher).should(never()).publishEvent(any(OrderCompletedEvent.class));
        assertFailed(503);
    }

    @Test
    @DisplayName("조회한 재고가 부족하면 공통 재고 부족 코드와 기존 주문 안내 메시지를 반환한다")
    void createOrderFailsWhenNoInventoryHasEnoughStock() {
        given(orderHistoryRepository.save(any(OrderHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(hubClient.findInventories(PRODUCT_ID))
                .willReturn(List.of(new InventoryInfo(UUID.randomUUID(), HUB_ID, PRODUCT_ID, 2)));

        assertThatThrownBy(() -> orderService.createOrder(createCommand(3)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", InventoryErrorCode.INSUFFICIENT_STOCK)
                .hasMessage("재고가 부족하여 주문할 수 없습니다.");

        then(orderRepository).shouldHaveNoInteractions();
        then(deliveryClient).shouldHaveNoInteractions();
        then(hubClient).should(never()).decreaseStock(any());
        then(hubClient).should(never()).restoreStock(any());
        then(eventPublisher).should(never()).publishEvent(any(OrderCompletedEvent.class));

        ArgumentCaptor<OrderHistory> historyCaptor = ArgumentCaptor.forClass(OrderHistory.class);
        then(orderHistoryRepository).should().save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getOperationStatus()).isEqualTo(OrderOperationStatus.FAILED);
        assertThat(historyCaptor.getValue().getResponseCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("재고 차감이 재고 부족으로 거절되면 409 예외가 발생하고 주문과 이력은 FAILED가 된다")
    void createOrderFailsWhenStockDecreaseRejected() {
        // given: 주문 저장까지는 성공, 차감 단계에서 재고 부족으로 거절
        givenOrderSavedSuccessfully();
        given(hubClient.decreaseStock(any(StockDecreaseCommand.class)))
                .willReturn(new StockOperationResult(UUID.randomUUID(), false, "INSUFFICIENT_STOCK", 2));

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(createCommand(3)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", InventoryErrorCode.INSUFFICIENT_STOCK)
                .hasMessage("재고가 부족하여 주문할 수 없습니다.");

        // then: 주문은 FAILED
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        then(orderRepository).should().save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getOrderStatus()).isEqualTo(OrderStatus.FAILED);

        // then: 이력은 FAILED, 409
        ArgumentCaptor<OrderHistory> historyCaptor = ArgumentCaptor.forClass(OrderHistory.class);
        then(orderHistoryRepository).should().save(historyCaptor.capture());
        OrderHistory history = historyCaptor.getValue();
        assertThat(history.getOperationStatus()).isEqualTo(OrderOperationStatus.FAILED);
        assertThat(history.getResponseCode()).isEqualTo(409);

        // then: 차감이 안 됐으니 배송도, 복원도 없음
        then(deliveryClient).shouldHaveNoInteractions();
        then(hubClient).should(never()).restoreStock(any());
        then(eventPublisher).should(never()).publishEvent(any(OrderCompletedEvent.class));
    }

    @Test
    @DisplayName("배송 생성이 실패하면 재고를 복원하고 503 예외가 발생하며 주문과 이력은 FAILED가 된다")
    void createOrderRestoresStockWhenDeliveryFails() {
        // given: 재고 차감까지 성공, 배송 생성 실패
        givenOrderSavedSuccessfully();
        given(hubClient.decreaseStock(any(StockDecreaseCommand.class)))
                .willReturn(new StockOperationResult(DECREASE_OPERATION_ID, true, null, 97));
        given(deliveryClient.createDelivery(any(CreateDeliveryCommand.class)))
                .willThrow(new BusinessException(OrderErrorCode.DELIVERY_FAILED));

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(createCommand(3)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.DELIVERY_FAILED);

        // then: 빠진 재고 복원
        then(hubClient).should().restoreStock(new StockRestoreCommand(ORDER_ID, DECREASE_OPERATION_ID));
        then(eventPublisher).should(never()).publishEvent(any(OrderCompletedEvent.class));
        assertFailed(503);
    }

    // ①~③ 이력 저장, 재고 조회, 주문 저장 까지는 정상 진행되도록 각본 준비
    private void givenOrderSavedSuccessfully() {
        given(orderHistoryRepository.save(any(OrderHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(hubClient.findInventories(PRODUCT_ID))
                .willReturn(List.of(new InventoryInfo(UUID.randomUUID(), HUB_ID, PRODUCT_ID, 100)));
        given(orderRepository.save(any(Order.class)))
                .willAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    ReflectionTestUtils.setField(order, "id", ORDER_ID);  // DB가 id를 채워주는 것을 흉내
                    return order;
                });
    }

    // 주문 FAILED + 이력 FAILED(응답코드) 공통 검증
    private void assertFailed(int expectedResponseCode) {
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        then(orderRepository).should().save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getOrderStatus()).isEqualTo(OrderStatus.FAILED);

        ArgumentCaptor<OrderHistory> historyCaptor = ArgumentCaptor.forClass(OrderHistory.class);
        then(orderHistoryRepository).should().save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getOperationStatus()).isEqualTo(OrderOperationStatus.FAILED);
        assertThat(historyCaptor.getValue().getResponseCode()).isEqualTo(expectedResponseCode);
    }


}
