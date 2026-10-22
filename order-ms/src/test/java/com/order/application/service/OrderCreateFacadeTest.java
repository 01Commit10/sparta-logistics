package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.InventoryErrorCode;
import com.common.exception.OrderErrorCode;
import com.order.application.dto.CreateOrderCommand;
import com.order.application.dto.OrderResult;
import com.order.domain.client.*;
import com.order.domain.entity.Order;
import com.order.domain.exception.DuplicateOrderRequestException;
import com.order.domain.vo.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OrderCreateFacadeTest {

    @Mock
    private OrderCreateTxService orderCreateTxService;

    @Mock
    private ProductClient productClient;

    @Mock
    private HubClient hubClient;

    @Mock
    private DeliveryClient deliveryClient;

    @InjectMocks
    private OrderCreateFacade orderCreateFacade;

    private static final UUID REQUESTER_COMPANY_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_COMPANY_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID HUB_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String IDEMPOTENCY_KEY = "order-req-0001";

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID HISTORY_ID = UUID.randomUUID();
    private static final UUID DELIVERY_ID = UUID.randomUUID();
    private static final UUID DECREASE_OPERATION_ID = UUID.randomUUID();
    private static final BigDecimal PRODUCT_PRICE = new BigDecimal("1000");

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
    @DisplayName("재고 조회, 차감, 배송 생성이 모두 성공하면 확정 트랜잭션의 결과(COMPLETED)를 돌려준다")
    void createOrderSuccess() {
        CreateOrderCommand command = createCommand(3);
        givenOrderSavedSuccessfully(command);
        given(hubClient.decreaseStock(any(StockDecreaseCommand.class)))
                .willReturn(new StockOperationResult(DECREASE_OPERATION_ID, true, null, 97));
        given(deliveryClient.createDelivery(any(CreateDeliveryCommand.class)))
                .willReturn(new DeliveryResult(DELIVERY_ID));
        OrderResult completed = completedResult();
        given(orderCreateTxService.complete(ORDER_ID, HISTORY_ID, DELIVERY_ID, DECREASE_OPERATION_ID))
                .willReturn(completed);

        OrderResult result = orderCreateFacade.createOrder(command);

        assertThat(result).isEqualTo(completed);
        then(orderCreateTxService).should().saveOrder(command, PRODUCT_PRICE, HISTORY_ID);
        then(hubClient).should().decreaseStock(new StockDecreaseCommand(ORDER_ID, HUB_ID, PRODUCT_ID, 3));
        then(deliveryClient).should().createDelivery(
                new CreateDeliveryCommand(ORDER_ID, HUB_ID, SUPPLIER_COMPANY_ID, REQUESTER_COMPANY_ID, USER_ID));
        then(hubClient).should(never()).restoreStock(any());
        then(orderCreateTxService).should(never()).fail(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("같은 키로 이미 성공한 요청이면 상품·재고·배송을 호출하지 않고 이전 결과를 반환한다")
    void createOrderReturnsPreviousResult() {
        CreateOrderCommand command = createCommand(3);
        OrderResult previous = completedResult();
        given(orderCreateTxService.findPreviousResult(command)).willReturn(Optional.of(previous));

        OrderResult result = orderCreateFacade.createOrder(command);

        assertThat(result).isEqualTo(previous);
        then(productClient).shouldHaveNoInteractions();
        then(hubClient).shouldHaveNoInteractions();
        then(deliveryClient).shouldHaveNoInteractions();
        then(orderCreateTxService).should(never()).claim(any());
    }

    @Test
    @DisplayName("같은 키의 동시 요청이 먼저 선점했으면 재고·배송을 호출하지 않고 앞 요청 상태로 판단한다 (처리 중 → 409)")
    void createOrderStopsBeforeRemoteCallsWhenKeyTakenConcurrently() {
        CreateOrderCommand command = createCommand(3);
        givenValidProduct();
        given(orderCreateTxService.findPreviousResult(command))
                .willReturn(Optional.empty())
                .willThrow(new BusinessException(OrderErrorCode.PREVIOUS_REQUEST_NOT_SUCCEEDED));
        given(orderCreateTxService.claim(command)).willThrow(new DuplicateOrderRequestException(IDEMPOTENCY_KEY));

        assertThatThrownBy(() -> orderCreateFacade.createOrder(command))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.PREVIOUS_REQUEST_NOT_SUCCEEDED);

        then(hubClient).shouldHaveNoInteractions();
        then(deliveryClient).shouldHaveNoInteractions();
        then(orderCreateTxService).should(never()).fail(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("같은 키의 동시 요청이 먼저 성공까지 끝냈으면 그 결과를 돌려준다")
    void createOrderReturnsResultOfConcurrentWinner() {
        CreateOrderCommand command = createCommand(3);
        givenValidProduct();
        OrderResult winner = completedResult();
        given(orderCreateTxService.findPreviousResult(command))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(winner));
        given(orderCreateTxService.claim(command)).willThrow(new DuplicateOrderRequestException(IDEMPOTENCY_KEY));

        assertThat(orderCreateFacade.createOrder(command)).isEqualTo(winner);
        then(hubClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("요청 단가와 현재 상품 단가가 다르면 409 예외가 발생하고 이력을 선점하지 않는다")
    void createOrderFailsWhenPriceChanged() {
        CreateOrderCommand command = createCommand(3);
        given(orderCreateTxService.findPreviousResult(command)).willReturn(Optional.empty());
        given(productClient.getProduct(PRODUCT_ID))
                .willReturn(new ProductInfo(PRODUCT_ID, SUPPLIER_COMPANY_ID, "테스트 상품", new BigDecimal("1500")));

        assertThatThrownBy(() -> orderCreateFacade.createOrder(command))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.PRICE_CHANGED)
                .hasMessageContaining("현재: 1500");

        then(orderCreateTxService).should(never()).claim(any());
        then(hubClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("상품의 업체와 요청한 공급업체가 다르면 400 예외가 발생하고 이력을 선점하지 않는다")
    void createOrderFailsWhenSupplierMismatch() {
        CreateOrderCommand command = createCommand(3);
        given(orderCreateTxService.findPreviousResult(command)).willReturn(Optional.empty());
        given(productClient.getProduct(PRODUCT_ID))
                .willReturn(new ProductInfo(PRODUCT_ID, UUID.randomUUID(), "다른 업체 상품", PRODUCT_PRICE));

        assertThatThrownBy(() -> orderCreateFacade.createOrder(command))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.SUPPLIER_PRODUCT_MISMATCH);

        then(orderCreateTxService).should(never()).claim(any());
        then(hubClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("조회한 재고가 부족하면 주문을 만들지 않고 이력만 FAILED(409)로 기록한다")
    void createOrderFailsWhenNoInventoryHasEnoughStock() {
        CreateOrderCommand command = createCommand(3);
        givenClaimed(command);
        given(hubClient.findInventories(PRODUCT_ID))
                .willReturn(List.of(new InventoryInfo(UUID.randomUUID(), HUB_ID, PRODUCT_ID, 2)));

        assertThatThrownBy(() -> orderCreateFacade.createOrder(command))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", InventoryErrorCode.INSUFFICIENT_STOCK)
                .hasMessage("재고가 부족하여 주문할 수 없습니다.");

        then(orderCreateTxService).should(never()).saveOrder(any(), any(), any());
        then(hubClient).should(never()).decreaseStock(any());
        then(hubClient).should(never()).restoreStock(any());
        then(orderCreateTxService).should().fail(eq(null), eq(HISTORY_ID), eq(409), anyString());
    }

    @Test
    @DisplayName("재고 차감이 재고 부족으로 거절되면 복원 없이 주문과 이력을 FAILED(409)로 기록한다")
    void createOrderFailsWhenStockDecreaseRejected() {
        CreateOrderCommand command = createCommand(3);
        givenOrderSavedSuccessfully(command);
        given(hubClient.decreaseStock(any(StockDecreaseCommand.class)))
                .willReturn(new StockOperationResult(null, false, "INSUFFICIENT_STOCK", 0));

        assertThatThrownBy(() -> orderCreateFacade.createOrder(command))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", InventoryErrorCode.INSUFFICIENT_STOCK);

        then(deliveryClient).shouldHaveNoInteractions();
        then(hubClient).should(never()).restoreStock(any());
        then(orderCreateTxService).should().fail(eq(ORDER_ID), eq(HISTORY_ID), eq(409), anyString());
    }

    @Test
    @DisplayName("재고 차감 결과를 모르면 같은 키로 다시 보내 차감 작업을 확인하고 복원한다")
    void createOrderRestoresStockWhenDecreaseResultUnknown() {
        CreateOrderCommand command = createCommand(3);
        givenOrderSavedSuccessfully(command);
        given(hubClient.decreaseStock(any(StockDecreaseCommand.class)))
                .willThrow(new BusinessException(OrderErrorCode.INVENTORY_UNAVAILABLE))
                .willReturn(new StockOperationResult(DECREASE_OPERATION_ID, true, null, 97));

        assertThatThrownBy(() -> orderCreateFacade.createOrder(command))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVENTORY_UNAVAILABLE);

        then(hubClient).should(times(2)).decreaseStock(new StockDecreaseCommand(ORDER_ID, HUB_ID, PRODUCT_ID, 3));
        then(hubClient).should().restoreStock(new StockRestoreCommand(ORDER_ID, DECREASE_OPERATION_ID));
        then(deliveryClient).shouldHaveNoInteractions();
        then(orderCreateTxService).should().fail(eq(ORDER_ID), eq(HISTORY_ID), eq(503), anyString());
    }

    @Test
    @DisplayName("결과를 모르는 차감을 다시 확인했을 때 차감되지 않았으면 복원을 호출하지 않는다")
    void createOrderSkipsRestoreWhenDecreaseConfirmedNotApplied() {
        CreateOrderCommand command = createCommand(3);
        givenOrderSavedSuccessfully(command);
        given(hubClient.decreaseStock(any(StockDecreaseCommand.class)))
                .willThrow(new BusinessException(OrderErrorCode.INVENTORY_UNAVAILABLE))
                .willReturn(new StockOperationResult(null, false, "INSUFFICIENT_STOCK", 0));

        assertThatThrownBy(() -> orderCreateFacade.createOrder(command))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVENTORY_UNAVAILABLE);

        then(hubClient).should(never()).restoreStock(any());
        then(orderCreateTxService).should().fail(eq(ORDER_ID), eq(HISTORY_ID), eq(503), anyString());
    }

    @Test
    @DisplayName("배송 생성이 실패하면 재고를 복원하고 주문과 이력을 FAILED(503)로 기록한다")
    void createOrderRestoresStockWhenDeliveryFails() {
        CreateOrderCommand command = createCommand(3);
        givenOrderSavedSuccessfully(command);
        given(hubClient.decreaseStock(any(StockDecreaseCommand.class)))
                .willReturn(new StockOperationResult(DECREASE_OPERATION_ID, true, null, 97));
        given(deliveryClient.createDelivery(any(CreateDeliveryCommand.class)))
                .willThrow(new BusinessException(OrderErrorCode.DELIVERY_FAILED));

        assertThatThrownBy(() -> orderCreateFacade.createOrder(command))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.DELIVERY_FAILED);

        then(hubClient).should().restoreStock(new StockRestoreCommand(ORDER_ID, DECREASE_OPERATION_ID));
        then(deliveryClient).should(never()).cancelDelivery(any());
        then(orderCreateTxService).should().fail(eq(ORDER_ID), eq(HISTORY_ID), eq(503), anyString());
    }

    @Test
    @DisplayName("배송까지 만든 뒤 확정 저장이 실패하면 배송을 취소하고 재고를 복원한 뒤 FAILED로 기록한다")
    void createOrderCompensatesWhenCompleteFails() {
        CreateOrderCommand command = createCommand(3);
        givenOrderSavedSuccessfully(command);
        given(hubClient.decreaseStock(any(StockDecreaseCommand.class)))
                .willReturn(new StockOperationResult(DECREASE_OPERATION_ID, true, null, 97));
        given(deliveryClient.createDelivery(any(CreateDeliveryCommand.class)))
                .willReturn(new DeliveryResult(DELIVERY_ID));
        RuntimeException commitFailure = new IllegalStateException("커밋 실패");
        given(orderCreateTxService.complete(ORDER_ID, HISTORY_ID, DELIVERY_ID, DECREASE_OPERATION_ID))
                .willThrow(commitFailure);

        assertThatThrownBy(() -> orderCreateFacade.createOrder(command)).isSameAs(commitFailure);

        then(deliveryClient).should().cancelDelivery(DELIVERY_ID);
        then(hubClient).should().restoreStock(new StockRestoreCommand(ORDER_ID, DECREASE_OPERATION_ID));
        then(orderCreateTxService).should().fail(eq(ORDER_ID), eq(HISTORY_ID), eq(500), anyString());
    }

    @Test
    @DisplayName("보상이나 실패 기록이 실패해도 원래 예외를 그대로 던진다")
    void createOrderKeepsOriginalExceptionWhenCompensationFails() {
        CreateOrderCommand command = createCommand(3);
        givenOrderSavedSuccessfully(command);
        given(hubClient.decreaseStock(any(StockDecreaseCommand.class)))
                .willReturn(new StockOperationResult(DECREASE_OPERATION_ID, true, null, 97));
        given(deliveryClient.createDelivery(any(CreateDeliveryCommand.class)))
                .willReturn(new DeliveryResult(DELIVERY_ID));
        RuntimeException commitFailure = new IllegalStateException("커밋 실패");
        given(orderCreateTxService.complete(ORDER_ID, HISTORY_ID, DELIVERY_ID, DECREASE_OPERATION_ID))
                .willThrow(commitFailure);
        willThrow(new BusinessException(OrderErrorCode.DELIVERY_UNAVAILABLE))
                .given(deliveryClient).cancelDelivery(DELIVERY_ID);
        given(hubClient.restoreStock(any())).willThrow(new BusinessException(OrderErrorCode.INVENTORY_UNAVAILABLE));
        willThrow(new IllegalStateException("DB 다운"))
                .given(orderCreateTxService).fail(any(), any(), anyInt(), any());

        assertThatThrownBy(() -> orderCreateFacade.createOrder(command)).isSameAs(commitFailure);
    }

    private void givenValidProduct() {
        given(productClient.getProduct(PRODUCT_ID))
                .willReturn(new ProductInfo(PRODUCT_ID, SUPPLIER_COMPANY_ID, "테스트 상품", PRODUCT_PRICE));
    }

    // 이전 이력 없음 → 상품 확인 통과 → 이력 선점
    private void givenClaimed(CreateOrderCommand command) {
        given(orderCreateTxService.findPreviousResult(command)).willReturn(Optional.empty());
        givenValidProduct();
        given(orderCreateTxService.claim(command)).willReturn(HISTORY_ID);
    }

    // 선점 → 재고 조회 → 주문 저장까지 정상
    private void givenOrderSavedSuccessfully(CreateOrderCommand command) {
        givenClaimed(command);
        given(hubClient.findInventories(PRODUCT_ID))
                .willReturn(List.of(new InventoryInfo(UUID.randomUUID(), HUB_ID, PRODUCT_ID, 100)));
        given(orderCreateTxService.saveOrder(command, PRODUCT_PRICE, HISTORY_ID)).willReturn(ORDER_ID);
    }

    private OrderResult completedResult() {
        Order order = Order.create(REQUESTER_COMPANY_ID, SUPPLIER_COMPANY_ID, PRODUCT_ID,
                3, PRODUCT_PRICE, "요청사항", USER_ID);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.complete(DELIVERY_ID, DECREASE_OPERATION_ID);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);
        return OrderResult.from(order);
    }
}
