package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.InventoryErrorCode;
import com.common.exception.OrderErrorCode;
import com.order.application.dto.CreateOrderCommand;
import com.order.application.dto.OrderResult;
import com.order.domain.client.*;
import com.order.domain.entity.Order;
import com.order.domain.entity.OrderHistory;
import com.order.domain.exception.DuplicateOrderRequestException;
import com.order.domain.repository.OrderHistoryRepository;
import com.order.domain.repository.OrderRepository;
import com.order.domain.vo.OrderOperationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;

    /**
     * 원격 호출(재고 조회·차감, 배송 생성)을 트랜잭션 밖에서 해서, 기다리는 동안 DB 커넥션을 잡지 않는다 (P02).
     * 짧은 트랜잭션 ① 선점(이력) ② 주문 저장 ③ 확정 또는 실패 기록으로 나눈다.
     * ①이 바로 커밋되므로 같은 키의 동시 요청은 원격 호출 전에 멈춘다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderResult createOrder(CreateOrderCommand command) {
        // ① 선점: 같은 키 이력이 있으면 이전 결과 반환 또는 거절, 없으면 IN_PROGRESS 이력 커밋
        Claim claim;
        try {
            claim = transactionTemplate.execute(status -> claim(command));
        } catch (DuplicateOrderRequestException e) {
            // 같은 키가 동시에 먼저 커밋됨: 이 트랜잭션은 롤백됐으니 새 트랜잭션에서 앞 요청 상태로 판단
            return transactionTemplate.execute(status -> orderHistoryRepository
                    .findByOperationTypeAndIdempotencyKey(OrderOperationType.CREATE, command.idempotencyKey())
                    .map(previous -> returnPreviousResult(previous, command))
                    .orElseThrow(() -> e));
        }
        if (claim.previous() != null) {
            return claim.previous();
        }
        UUID historyId = claim.historyId();

        UUID orderId = null;
        boolean stockMayBeDecreased = false;            // 재고가 빠졌을 "가능성"이 있으면 true -> 실패 시 복원 대상
        StockDecreaseCommand decreaseCommand = null;    // 결과를 모를 때 같은 키로 다시 보내기 위해 보관
        UUID decreaseOperationId = null;                // 차감 성공 시 hub 작업 ID -> 복원에 필요
        try {
            // 재고 조회 (원격, 차감할 허브 선택)
            InventoryInfo inventoryInfo = selectInventory(command);

            // ② 주문 PROCESSING 저장 + 이력에 연결
            orderId = transactionTemplate.execute(status -> saveOrder(command, historyId));

            // 재고 차감 (원격)
            decreaseCommand = new StockDecreaseCommand(
                    orderId, inventoryInfo.hubId(), command.productId(), command.quantity());
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

            // 배송 생성 (원격)
            DeliveryResult delivery = deliveryClient.createDelivery(new CreateDeliveryCommand(
                    orderId,
                    inventoryInfo.hubId(),
                    command.supplierCompanyId(),
                    command.requesterCompanyId(),
                    command.orderedBy()
            ));

            // ③ 주문 COMPLETED + 이력 SUCCEEDED
            UUID completedOrderId = orderId;
            return transactionTemplate.execute(status -> complete(completedOrderId, historyId, delivery.deliveryId()));
        } catch (RuntimeException e) {
            // 보상: 재고가 빠졌을 수 있으면 복원(원격) → ③ 주문·이력 FAILED → 원래 예외 그대로 던짐
            if (stockMayBeDecreased) {
                restoreStockSafely(decreaseCommand, decreaseOperationId);
            }
            recordFailureSafely(orderId, historyId, e);
            throw e;
        }
    }

    // ① 선점 트랜잭션
    private Claim claim(CreateOrderCommand command) {
        Optional<OrderHistory> previous = orderHistoryRepository
                .findByOperationTypeAndIdempotencyKey(OrderOperationType.CREATE, command.idempotencyKey());
        if (previous.isPresent()) {
            return new Claim(null, returnPreviousResult(previous.get(), command));
        }
        // 바로 반영해 같은 키의 동시 요청을 원격 호출 전에 막는다 (중복이면 DuplicateOrderRequestException → 롤백)
        OrderHistory history = orderHistoryRepository.saveAndFlush(OrderHistory.create(
                OrderOperationType.CREATE, command.idempotencyKey(), command.requestHash()));
        return new Claim(history.getId(), null);
    }

    // ② 주문 저장 트랜잭션
    private UUID saveOrder(CreateOrderCommand command, UUID historyId) {
        Order order = orderRepository.save(Order.create(
                command.requesterCompanyId(),
                command.supplierCompanyId(),
                command.productId(),
                command.quantity(),
                command.unitPrice(),
                command.requestNote(),
                command.orderedBy()
        ));
        findHistory(historyId).linkOrder(order);
        return order.getId();
    }

    // ③ 확정 트랜잭션
    private OrderResult complete(UUID orderId, UUID historyId, UUID deliveryId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.complete(deliveryId);
        findHistory(historyId).succeed(201);
        return OrderResult.from(order);
    }

    // ③ 실패 기록 트랜잭션. 기록 실패가 원래 예외를 덮어쓰지 않도록 로그만 남긴다 (이력은 IN_PROGRESS로 남음)
    private void recordFailureSafely(UUID orderId, UUID historyId, RuntimeException cause) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (orderId != null) {
                    orderRepository.findById(orderId).orElseThrow().fail();
                }
                findHistory(historyId).fail(responseCodeOf(cause), cause.getMessage());
            });
        } catch (RuntimeException recordError) {
            log.error("[주문 실패 기록 실패] historyId={} orderId={}", historyId, orderId, recordError);
        }
    }

    private OrderHistory findHistory(UUID historyId) {
        return orderHistoryRepository.findById(historyId).orElseThrow();
    }

    // 선점 결과: 새로 선점한 이력 ID 또는 같은 키의 이전 결과
    private record Claim(UUID historyId, OrderResult previous) {
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
