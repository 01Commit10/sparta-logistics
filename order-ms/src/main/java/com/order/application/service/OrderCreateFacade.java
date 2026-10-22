package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.InventoryErrorCode;
import com.common.exception.OrderErrorCode;
import com.order.application.dto.CreateOrderCommand;
import com.order.application.dto.OrderResult;
import com.order.domain.client.*;
import com.order.domain.exception.DuplicateOrderRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * 주문 생성 Saga 흐름. 원격 호출(상품, 재고, 배송)과 보상만 맡고 트랜잭션을 열지 않는다.
 * DB 작업은 OrderCreateTxService의 짧은 트랜잭션(선점 → 주문 저장 → 확정/실패 기록)으로 나눠,
 * 원격 응답을 기다리는 동안 DB 커넥션을 잡지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreateFacade {

    private final OrderCreateTxService orderCreateTxService;
    private final ProductClient productClient;
    private final HubClient hubClient;
    private final DeliveryClient deliveryClient;

    public OrderResult createOrder(CreateOrderCommand command) {
        // 0. 멱등성: 같은 키로 처리한 이력이 있으면 이전 결과 반환 또는 거절
        Optional<OrderResult> previous = orderCreateTxService.findPreviousResult(command);
        if (previous.isPresent()) {
            return previous.get();
        }

        // 상품 조회 + 공급업체·가격 확인
        ProductInfo product = productClient.getProduct(command.productId());
        validateProduct(product, command);

        // ① 이력 IN_PROGRESS 선점 (바로 커밋) ← 번호표: 중복 차단 + 이후 모든 성공/실패를 기록할 자리 확보
        UUID historyId;
        try {
            historyId = orderCreateTxService.claim(command);
        } catch (DuplicateOrderRequestException e) {
            // 같은 키가 동시에 먼저 선점됨: 선점 트랜잭션은 롤백됐으니 새 트랜잭션에서 앞 요청 상태로 판단 (성공이면 그 결과, 아니면 409)
            log.warn("[주문 중복 요청] {}", e.getMessage());
            return orderCreateTxService.findPreviousResult(command)
                    .orElseThrow(() -> new BusinessException(OrderErrorCode.PREVIOUS_REQUEST_NOT_SUCCEEDED));
        }

        UUID orderId = null;
        boolean stockMayBeDecreased = false;            // 재고가 빠졌을 "가능성"이 있으면 true -> 실패 시 복원 대상
        StockDecreaseCommand decreaseCommand = null;    // 결과를 모를 때 같은 키로 다시 보내기 위해 보관
        UUID decreaseOperationId = null;                // 차감 성공 시 hub 작업 ID -> 복원에 필요
        UUID deliveryId = null;                         // 배송을 만든 뒤 확정 저장이 실패하면 취소 대상
        try {
            // 2. 재고 조회 (차감 할 허브 선택)
            InventoryInfo inventoryInfo = selectInventory(command);

            // ② 주문 PROCESSING 저장 + 이력에 연결
            orderId = orderCreateTxService.saveOrder(command, product.unitPrice(), historyId);

            // 4. 재고 차감
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

            // 5. 배송 생성
            deliveryId = deliveryClient.createDelivery(new CreateDeliveryCommand(
                    orderId,
                    inventoryInfo.hubId(),
                    command.supplierCompanyId(),
                    command.requesterCompanyId(),
                    command.orderedBy()
            )).deliveryId();

            // ③ 주문 COMPLETED + 이력 SUCCEEDED
            return orderCreateTxService.complete(orderId, historyId, deliveryId, decreaseOperationId);
        } catch (RuntimeException e) {
            // 보상: 만든 배송 취소 → 빠졌을 수 있는 재고 복원 → ③ 주문·이력 FAILED → 원래 예외 그대로 던짐
            if (deliveryId != null) {
                cancelDeliverySafely(orderId, deliveryId);
            }
            if (stockMayBeDecreased) {
                restoreStockSafely(decreaseCommand, decreaseOperationId);
            }
            recordFailureSafely(orderId, historyId, e);
            throw e;
        }
    }

    private void validateProduct(ProductInfo product, CreateOrderCommand command) {
        if (!product.isSuppliedBy(command.supplierCompanyId())) {
            throw new BusinessException(OrderErrorCode.SUPPLIER_PRODUCT_MISMATCH);
        }
        // 사용자가 본 가격과 현재 상품 가격이 다르면 거절 → 최신 가격 확인 후 다시 주문
        if (!product.isPricedAt(command.unitPrice())) {
            throw new BusinessException(OrderErrorCode.PRICE_CHANGED,
                    "상품 가격이 변경되었습니다. (요청: %s, 현재: %s)"
                            .formatted(command.unitPrice().toPlainString(), product.unitPrice().toPlainString()));
        }
    }

    private int responseCodeOf(RuntimeException e) {
        if (e instanceof BusinessException businessException) {
            return businessException.getErrorCode().getHttpStatus().value();
        }
        return 500;
    }

    // 배송 취소 실패가 원래 예외를 덮어쓰지 않도록 로그만 남긴다
    private void cancelDeliverySafely(UUID orderId, UUID deliveryId) {
        try {
            deliveryClient.cancelDelivery(deliveryId);
        } catch (RuntimeException cancelError) {
            log.error("[주문 보상 실패] 배송 취소 실패 orderId={} deliveryId={}", orderId, deliveryId, cancelError);
        }
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
                return;
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

    // 기록 실패가 원래 예외를 덮어쓰지 않도록 로그만 남긴다 (이력은 IN_PROGRESS로 남음)
    private void recordFailureSafely(UUID orderId, UUID historyId, RuntimeException cause) {
        try {
            orderCreateTxService.fail(orderId, historyId, responseCodeOf(cause), cause.getMessage());
        } catch (RuntimeException recordError) {
            log.error("[주문 실패 기록 실패] historyId={} orderId={}", historyId, orderId, recordError);
        }
    }

    private InventoryInfo selectInventory(CreateOrderCommand command) {
        return hubClient.findInventories(command.productId()).stream()
                .filter(inventoryInfo -> inventoryInfo.hasEnough(command.quantity()))
                .findFirst()
                .orElseThrow(OrderCreateFacade::insufficientStockException);
    }

    // 재고 부족 코드는 공통으로 사용하고, 주문 API의 기존 안내 메시지는 유지한다.
    private static BusinessException insufficientStockException() {
        return new BusinessException(
                InventoryErrorCode.INSUFFICIENT_STOCK,
                "재고가 부족하여 주문할 수 없습니다."
        );
    }
}
