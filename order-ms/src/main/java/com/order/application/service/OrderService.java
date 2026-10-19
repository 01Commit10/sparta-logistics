package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.OrderErrorCode;
import com.order.application.dto.CreateOrderCommand;
import com.order.application.dto.OrderResult;
import com.order.domain.client.*;
import com.order.domain.entity.Order;
import com.order.domain.entity.OrderHistory;
import com.order.domain.repository.OrderHistoryRepository;
import com.order.domain.repository.OrderRepository;
import com.order.domain.vo.OrderOperationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final HubClient hubClient;
    private final DeliveryClient deliveryClient;

    // BusinessException으로 실패하면 롤백하지 않고 FAILED 상태를 커밋한다
    @Transactional(noRollbackFor = BusinessException.class)
    public OrderResult createOrder(CreateOrderCommand command) {
        // 0. 멱등성: 같은 키로 처리한 이력이 있으면 이전 결과 반환 또는 거절
        Optional<OrderHistory> previous = orderHistoryRepository
                .findByOperationTypeAndIdempotencyKey(OrderOperationType.CREATE, command.idempotencyKey());
        if (previous.isPresent()) {
            return returnPreviousResult(previous.get(), command);
        }

        // 1. 이력 IN_PROCESSING 저장 ← 번호표: 중복 차단 + 이후 모든 성공/실패를 기록할 자리 확보
        OrderHistory history = OrderHistory.create(
                OrderOperationType.CREATE, command.idempotencyKey(), command.requestHash());
        orderHistoryRepository.save(history);

        Order order = null;
        // 재고가 빠졌을 "가능성"이 있으면 true -> 실패 시 복원 대상
        boolean stockMayBeDecreased = false;
        try {
            // 2. 재고 조회 (차감 할 허브 선택)
            InventoryInfo inventoryInfo = selectInventory(command);

            // 3. 주문 PROCESSING 저장 + 이력에 연결
            order = orderRepository.save(Order.create(
                    command.requesterCompanyId(),
                    command.supplierCompanyId(),
                    command.productId(),
                    command.quantity(),
                    command.unitPrice(),
                    command.requestNote(),
                    command.orderedBy()
            ));
            history.linkOrder(order);

            // 4. 재고 차감
            stockMayBeDecreased = true;
            StockOperationResult stock = hubClient.decreaseStock(new StockDecreaseCommand(
                    order.getId(), inventoryInfo.hubId(), command.productId(), command.quantity()));
            if (!stock.succeeded()) {
                stockMayBeDecreased = false;
                throw new BusinessException(stock.isInsufficientStock()
                        ? OrderErrorCode.INSUFFICIENT_STOCK
                        : OrderErrorCode.INVENTORY_UNAVAILABLE);
            }

            // 5. 배송 생성
            DeliveryResult delivery = deliveryClient.createDelivery(new CreateDeliveryCommand(
                    order.getId(),
                    inventoryInfo.hubId(),
                    command.supplierCompanyId(),
                    command.requesterCompanyId(),
                    command.orderedBy()
            ));

            // 6. 주문 COMPLETED + 이력 SUCCEEDED
            order.complete(delivery.deliveryId());
            history.succeed(201);
            return OrderResult.from(order);
        } catch (RuntimeException e) {
            // 보상: 재고가 빠졌을 수 있으면 복원 → 주문·이력 FAILED → 원래 예외 그대로 던짐
            if (stockMayBeDecreased) {
                restoreStockSafely(order);
            }
            if (order != null) {
                order.fail();
            }
            history.fail(responseCodeOf(e), e.getMessage());
            throw e;
        }
    }

    private int responseCodeOf(RuntimeException e) {
        if (e instanceof BusinessException businessException) {
            return businessException.getErrorCode().getHttpStatus().value();
        }
        return 500;
    }

    /**
     * 복원 실패가 원래 예외를 덮어쓰지 않도록 로그만 남긴다.
     */
    private void restoreStockSafely(Order order) {
        try {
            hubClient.restoreStock(new StockRestoreCommand(order.getId()));
        } catch (RuntimeException restoreError) {
            log.error("[주문 보상 실패] 재고 복원 실패 orderId={}", order.getId(), restoreError);
        }

    }

    /**
     * 같은 키의 이전 요청: 내용이 같고 성공했을 때만 이전 결과를 돌려준다.
     */
    private OrderResult returnPreviousResult(OrderHistory previousHistory, CreateOrderCommand command) {
        if (!previousHistory.isSameReqeust(command.requestHash())) {
            throw new BusinessException(OrderErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        if (!previousHistory.isSucceeded()) {
            throw new BusinessException(OrderErrorCode.PREVIOUS_REQUEST_NOT_SUCCEEDED);
        }
        return OrderResult.from(previousHistory.getOrder());
    }

    private InventoryInfo selectInventory(CreateOrderCommand command) {
        return hubClient.findInventories(command.productId()).stream()
                .filter(inventoryInfo -> inventoryInfo.hasEnough(command.quantity()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK));
    }


}
