package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.InventoryErrorCode;
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
import java.util.UUID;

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
        boolean stockMayBeDecreased = false;            // 재고가 빠졌을 "가능성"이 있으면 true -> 실패 시 복원 대상
        StockDecreaseCommand decreaseCommand = null;    // 결과를 모를 때 같은 키로 다시 보내기 위해 보관
        UUID decreaseOperationId = null;                // 차감 성공 시 hub 작업 ID -> 복원에 필요
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
            decreaseCommand = new StockDecreaseCommand(
                    order.getId(), inventoryInfo.hubId(), command.productId(), command.quantity());
            stockMayBeDecreased = true;
            StockOperationResult stock = hubClient.decreaseStock(decreaseCommand);
            if (!stock.succeeded()) {
                stockMayBeDecreased = false;
                if (stock.isInsufficientStock()) {
                    throw insufficientStockException();
                }
                throw new BusinessException(OrderErrorCode.INVENTORY_UNAVAILABLE);
            }
            decreaseOperationId = stock.operationId();

            // 5. 배송 생성
            DeliveryResult delivery = deliveryClient.createDelivery(new CreateDeliveryCommand(
                    order.getId(),
                    inventoryInfo.hubId(),
                    command.supplierCompanyId(),
                    command.requesterCompanyId(),
                    command.orderedBy()
            ));

            // 6. 주문 COMPLETED + 이력 SUCCEEDED
            order.complete(delivery.deliveryId(), decreaseOperationId);
            history.succeed(201);
            return OrderResult.from(order);
        } catch (RuntimeException e) {
            // 보상: 재고가 빠졌을 수 있으면 복원 → 주문·이력 FAILED → 원래 예외 그대로 던짐
            if (stockMayBeDecreased) {
                restoreStockSafely(decreaseCommand, decreaseOperationId);
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
     * 차감 작업 ID를 모르면 (타임아웃 등) 같은 멱등 키로 차감을 다시 보내서 hub에 기록된 결과를 확인한다.
     */
    private void restoreStockSafely(StockDecreaseCommand decreaseCommand, UUID decreaseOperationId) {
        UUID orderId = decreaseCommand.orderId();
        try {
            UUID operationId = (decreaseOperationId != null)
                    ? decreaseOperationId : confirmDecreaseOperationId(decreaseCommand);
            if (operationId == null) {
                log.info("[주문 보상] 차감되지 않아 복원 불필요 orderId={}", orderId);
            }
            hubClient.restoreStock(new StockRestoreCommand(orderId, operationId));
        } catch (RuntimeException restoreError) {
            log.error("[주문 보상 실패] 재고 복원 실패 orderId={}", orderId, restoreError);
        }
    }

    private UUID confirmDecreaseOperationId(StockDecreaseCommand decreaseCommand) {
        StockOperationResult replay = hubClient.decreaseStock(decreaseCommand);
        return replay.succeeded() ? replay.operationId() : null;
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
                .orElseThrow(OrderService::insufficientStockException);
    }

    // 재고 부족 코드는 공통으로 사용하고, 주문 API의 기존 안내 메시지는 유지한다.
    private static BusinessException insufficientStockException() {
        return new BusinessException(
                InventoryErrorCode.INSUFFICIENT_STOCK,
                "재고가 부족하여 주문할 수 없습니다."
        );
    }

}
