package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.OrderErrorCode;
import com.order.application.dto.CreateOrderCommand;
import com.order.application.dto.OrderResult;
import com.order.domain.client.*;
import com.order.domain.entity.OrderHistory;
import com.order.domain.exception.DuplicateOrderRequestException;
import com.order.domain.repository.OrderHistoryRepository;
import com.order.domain.vo.OrderOperationStatus;
import com.order.domain.vo.OrderOperationType;
import com.order.domain.vo.OrderStatus;
import com.order.infrastructure.persistence.OrderHistoryJpaRepository;
import com.order.infrastructure.persistence.OrderJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 실제 트랜잭션 매니저(H2)로 주문 생성의 트랜잭션 경계를 확인한다.
 * - 원격 호출 중에는 트랜잭션이 없다 (DB 커넥션을 잡지 않는다)
 * - 원격 호출 전에 멱등키 선점이 이미 커밋되어 있다
 */
@SpringBootTest
class OrderCreateTransactionBoundaryTest {

    @Autowired
    private OrderCreateFacade orderCreateFacade;

    @Autowired
    private OrderCreateTxService orderCreateTxService;

    @Autowired
    private OrderHistoryRepository orderHistoryRepository;

    @Autowired
    private OrderHistoryJpaRepository orderHistoryJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @MockitoBean
    private ProductClient productClient;

    @MockitoBean
    private HubClient hubClient;

    @MockitoBean
    private DeliveryClient deliveryClient;

    @MockitoBean
    private UserClient userClient;

    private static final UUID SUPPLIER_COMPANY_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID HUB_ID = UUID.randomUUID();
    private static final BigDecimal PRODUCT_PRICE = new BigDecimal("1000");

    @AfterEach
    void tearDown() {
        orderHistoryJpaRepository.deleteAll();
        orderJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("원격 호출(상품·재고 조회·차감·배송 생성)은 트랜잭션 밖에서 하고, 그 전에 이력 선점이 커밋되어 있다")
    void remoteCallsRunOutsideTransaction() {
        CreateOrderCommand command = command("boundary-key-1");
        List<Boolean> txActiveDuringRemoteCalls = new ArrayList<>();
        List<OrderOperationStatus> committedHistoryDuringRemoteCall = new ArrayList<>();

        given(productClient.getProduct(PRODUCT_ID)).willAnswer(invocation -> {
            txActiveDuringRemoteCalls.add(TransactionSynchronizationManager.isActualTransactionActive());
            return new ProductInfo(PRODUCT_ID, SUPPLIER_COMPANY_ID, "테스트 상품", PRODUCT_PRICE);
        });
        given(hubClient.findInventories(PRODUCT_ID)).willAnswer(invocation -> {
            txActiveDuringRemoteCalls.add(TransactionSynchronizationManager.isActualTransactionActive());
            // 트랜잭션 밖 조회라 커밋된 데이터만 보인다
            orderHistoryJpaRepository.findByOperationTypeAndIdempotencyKey(OrderOperationType.CREATE, "boundary-key-1")
                    .ifPresent(history -> committedHistoryDuringRemoteCall.add(history.getOperationStatus()));
            return List.of(new InventoryInfo(UUID.randomUUID(), HUB_ID, PRODUCT_ID, 100));
        });
        given(hubClient.decreaseStock(any(StockDecreaseCommand.class))).willAnswer(invocation -> {
            txActiveDuringRemoteCalls.add(TransactionSynchronizationManager.isActualTransactionActive());
            return new StockOperationResult(UUID.randomUUID(), true, null, 97);
        });
        given(deliveryClient.createDelivery(any(CreateDeliveryCommand.class))).willAnswer(invocation -> {
            txActiveDuringRemoteCalls.add(TransactionSynchronizationManager.isActualTransactionActive());
            return new DeliveryResult(UUID.randomUUID());
        });

        OrderResult result = orderCreateFacade.createOrder(command);

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(txActiveDuringRemoteCalls).hasSize(4).containsOnly(false);
        assertThat(committedHistoryDuringRemoteCall).containsExactly(OrderOperationStatus.IN_PROGRESS);
        assertThat(orderHistoryJpaRepository.findByOperationTypeAndIdempotencyKey(OrderOperationType.CREATE, "boundary-key-1"))
                .get().extracting(OrderHistory::getOperationStatus).isEqualTo(OrderOperationStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("배송 생성이 실패하면 주문과 이력의 FAILED가 커밋된다")
    void failureIsCommitted() {
        CreateOrderCommand command = command("boundary-key-2");
        given(productClient.getProduct(PRODUCT_ID))
                .willReturn(new ProductInfo(PRODUCT_ID, SUPPLIER_COMPANY_ID, "테스트 상품", PRODUCT_PRICE));
        given(hubClient.findInventories(PRODUCT_ID))
                .willReturn(List.of(new InventoryInfo(UUID.randomUUID(), HUB_ID, PRODUCT_ID, 100)));
        given(hubClient.decreaseStock(any(StockDecreaseCommand.class)))
                .willReturn(new StockOperationResult(UUID.randomUUID(), true, null, 97));
        given(deliveryClient.createDelivery(any(CreateDeliveryCommand.class)))
                .willThrow(new BusinessException(OrderErrorCode.DELIVERY_FAILED));
        given(hubClient.restoreStock(any(StockRestoreCommand.class)))
                .willReturn(new StockOperationResult(UUID.randomUUID(), true, null, 100));

        assertThatThrownBy(() -> orderCreateFacade.createOrder(command))
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.DELIVERY_FAILED);

        assertThat(orderJpaRepository.findAll())
                .singleElement().extracting("orderStatus").isEqualTo(OrderStatus.FAILED);
        assertThat(orderHistoryJpaRepository.findByOperationTypeAndIdempotencyKey(OrderOperationType.CREATE, "boundary-key-2"))
                .get().extracting(OrderHistory::getOperationStatus).isEqualTo(OrderOperationStatus.FAILED);
    }

    @Test
    @DisplayName("같은 키로 이미 선점된 이력이 있으면 선점이 중복 주문 요청 예외로 실패한다 (실제 유니크 제약)")
    void claimRejectsDuplicateKey() {
        CreateOrderCommand command = command("boundary-key-3");
        orderCreateTxService.claim(command);

        assertThatThrownBy(() -> orderCreateTxService.claim(command))
                .isInstanceOf(DuplicateOrderRequestException.class);
        assertThat(orderHistoryRepository.findByOperationTypeAndIdempotencyKey(OrderOperationType.CREATE, "boundary-key-3"))
                .isPresent();
    }

    private CreateOrderCommand command(String idempotencyKey) {
        return new CreateOrderCommand(UUID.randomUUID(), SUPPLIER_COMPANY_ID, PRODUCT_ID, 3,
                PRODUCT_PRICE, "요청사항", UUID.randomUUID(), idempotencyKey);
    }
}
