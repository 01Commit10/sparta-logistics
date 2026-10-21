package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.OrderErrorCode;
import com.order.application.dto.CreateOrderCommand;
import com.order.application.dto.OrderResult;
import com.order.application.event.OrderCompletedEvent;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrderCreateTxServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderHistoryRepository orderHistoryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderCreateTxService orderCreateTxService;

    private static final UUID REQUESTER_COMPANY_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_COMPANY_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String IDEMPOTENCY_KEY = "order-req-0001";

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID HISTORY_ID = UUID.randomUUID();
    private static final UUID DELIVERY_ID = UUID.randomUUID();
    private static final UUID DECREASE_OPERATION_ID = UUID.randomUUID();
    private static final BigDecimal PRODUCT_PRICE = new BigDecimal("1000");

    private final CreateOrderCommand command = new CreateOrderCommand(
            REQUESTER_COMPANY_ID, SUPPLIER_COMPANY_ID, PRODUCT_ID, 3,
            new BigDecimal("1000.00"), "요청사항", USER_ID, IDEMPOTENCY_KEY);

    @Test
    @DisplayName("같은 키의 이력이 없으면 이전 결과는 비어 있다")
    void findPreviousResultEmptyWhenNoHistory() {
        given(orderHistoryRepository.findByOperationTypeAndIdempotencyKey(OrderOperationType.CREATE, IDEMPOTENCY_KEY))
                .willReturn(Optional.empty());

        assertThat(orderCreateTxService.findPreviousResult(command)).isEmpty();
    }

    @Test
    @DisplayName("같은 키로 성공한 같은 요청이면 이전 주문 결과를 돌려준다")
    void findPreviousResultReturnsSucceededOrder() {
        Order order = order();
        order.complete(DELIVERY_ID, DECREASE_OPERATION_ID);
        OrderHistory history = history();
        history.linkOrder(order);
        history.succeed(201);
        given(orderHistoryRepository.findByOperationTypeAndIdempotencyKey(OrderOperationType.CREATE, IDEMPOTENCY_KEY))
                .willReturn(Optional.of(history));

        Optional<OrderResult> result = orderCreateTxService.findPreviousResult(command);

        assertThat(result).isPresent();
        assertThat(result.get().orderId()).isEqualTo(ORDER_ID);
        assertThat(result.get().orderStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderOperationStatus.class, names = {"IN_PROGRESS", "FAILED"})
    @DisplayName("같은 키의 같은 요청이 진행 중이거나 실패했으면 409 PREVIOUS_REQUEST_NOT_SUCCEEDED")
    void findPreviousResultRejectsNotSucceeded(OrderOperationStatus previousStatus) {
        OrderHistory history = history();
        if (previousStatus == OrderOperationStatus.FAILED) {
            history.fail(503, "이전 요청 처리 실패");
        }
        given(orderHistoryRepository.findByOperationTypeAndIdempotencyKey(OrderOperationType.CREATE, IDEMPOTENCY_KEY))
                .willReturn(Optional.of(history));

        assertThatThrownBy(() -> orderCreateTxService.findPreviousResult(command))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.PREVIOUS_REQUEST_NOT_SUCCEEDED);
    }

    @Test
    @DisplayName("같은 키로 다른 내용의 요청이면 409 IDEMPOTENCY_KEY_CONFLICT")
    void findPreviousResultRejectsDifferentRequest() {
        OrderHistory history = OrderHistory.create(OrderOperationType.CREATE, IDEMPOTENCY_KEY, "b".repeat(64));
        given(orderHistoryRepository.findByOperationTypeAndIdempotencyKey(OrderOperationType.CREATE, IDEMPOTENCY_KEY))
                .willReturn(Optional.of(history));

        assertThatThrownBy(() -> orderCreateTxService.findPreviousResult(command))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }

    @Test
    @DisplayName("선점은 IN_PROGRESS 이력을 즉시 반영(saveAndFlush)하고 이력 ID를 돌려준다")
    void claimSavesAndFlushesHistory() {
        given(orderHistoryRepository.saveAndFlush(any(OrderHistory.class)))
                .willAnswer(invocation -> {
                    OrderHistory saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", HISTORY_ID);
                    return saved;
                });

        UUID historyId = orderCreateTxService.claim(command);

        assertThat(historyId).isEqualTo(HISTORY_ID);
        ArgumentCaptor<OrderHistory> captor = ArgumentCaptor.forClass(OrderHistory.class);
        then(orderHistoryRepository).should().saveAndFlush(captor.capture());
        assertThat(captor.getValue().getOperationStatus()).isEqualTo(OrderOperationStatus.IN_PROGRESS);
        assertThat(captor.getValue().getRequestHash()).isEqualTo(command.requestHash());
        then(orderHistoryRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("주문 저장은 상품 단가로 PROCESSING 주문을 만들고 이력에 연결한다")
    void saveOrderLinksHistory() {
        OrderHistory history = history();
        given(orderHistoryRepository.findById(HISTORY_ID)).willReturn(Optional.of(history));
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", ORDER_ID);
            return saved;
        });

        UUID orderId = orderCreateTxService.saveOrder(command, PRODUCT_PRICE, HISTORY_ID);

        assertThat(orderId).isEqualTo(ORDER_ID);
        assertThat(history.getOrder().getOrderStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(history.getOrder().getUnitPriceSnapshot()).isEqualByComparingTo(PRODUCT_PRICE);
    }

    @Test
    @DisplayName("확정은 주문 COMPLETED, 이력 SUCCEEDED(201)로 바꾸고 완료 이벤트를 발행한다")
    void completeMarksOrderAndHistory() {
        Order order = order();
        OrderHistory history = history();
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(orderHistoryRepository.findById(HISTORY_ID)).willReturn(Optional.of(history));

        OrderResult result = orderCreateTxService.complete(ORDER_ID, HISTORY_ID, DELIVERY_ID, DECREASE_OPERATION_ID);

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(result.deliveryId()).isEqualTo(DELIVERY_ID);
        assertThat(order.getStockOperationId()).isEqualTo(DECREASE_OPERATION_ID);
        assertThat(history.getOperationStatus()).isEqualTo(OrderOperationStatus.SUCCEEDED);
        assertThat(history.getResponseCode()).isEqualTo(201);
        then(eventPublisher).should().publishEvent(any(OrderCompletedEvent.class));
    }

    @Test
    @DisplayName("실패 기록은 주문과 이력을 FAILED로 바꾼다")
    void failMarksOrderAndHistory() {
        Order order = order();
        OrderHistory history = history();
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(orderHistoryRepository.findById(HISTORY_ID)).willReturn(Optional.of(history));

        orderCreateTxService.fail(ORDER_ID, HISTORY_ID, 503, "배송 생성 실패");

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(history.getOperationStatus()).isEqualTo(OrderOperationStatus.FAILED);
        assertThat(history.getResponseCode()).isEqualTo(503);
        assertThat(history.getLastError()).isEqualTo("배송 생성 실패");
    }

    @Test
    @DisplayName("주문을 만들기 전에 실패했으면 이력만 FAILED로 바꾼다")
    void failWithoutOrderMarksHistoryOnly() {
        OrderHistory history = history();
        given(orderHistoryRepository.findById(HISTORY_ID)).willReturn(Optional.of(history));

        orderCreateTxService.fail(null, HISTORY_ID, 409, "재고 부족");

        assertThat(history.getOperationStatus()).isEqualTo(OrderOperationStatus.FAILED);
        then(orderRepository).shouldHaveNoInteractions();
    }

    private Order order() {
        Order order = Order.create(REQUESTER_COMPANY_ID, SUPPLIER_COMPANY_ID, PRODUCT_ID,
                3, PRODUCT_PRICE, "요청사항", USER_ID);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }

    private OrderHistory history() {
        OrderHistory history = OrderHistory.create(OrderOperationType.CREATE, IDEMPOTENCY_KEY, command.requestHash());
        ReflectionTestUtils.setField(history, "id", HISTORY_ID);
        return history;
    }
}
