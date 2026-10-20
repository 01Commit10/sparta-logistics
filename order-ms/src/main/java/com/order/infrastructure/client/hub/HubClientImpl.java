package com.order.infrastructure.client.hub;

import com.common.exception.BusinessException;
import com.common.exception.InventoryErrorCode;
import com.common.exception.OrderErrorCode;
import com.common.remote.RemoteBusinessException;
import com.common.remote.RemoteCallTemplate;
import com.common.remote.RemoteServiceException;
import com.common.remote.RemoteTimeoutException;
import com.order.domain.client.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Profile("!stub")
@RequiredArgsConstructor
public class HubClientImpl implements HubClient {

    private static final String TARGET = "hub-ms";

    private final HubFeignClient hubFeignClient;
    private final RemoteCallTemplate remoteCallTemplate;


    @Override
    public List<InventoryInfo> findInventories(UUID productId) {
        try {
            return remoteCallTemplate.execute(TARGET, () -> hubFeignClient.getInventories(productId))
                    .stream()
                    .map(i -> new InventoryInfo(i.id(), i.hubId(), i.productId(), i.quantity()))
                    .toList();
        } catch (RemoteServiceException | RemoteTimeoutException e) {
            throw unavailable(e);
        }
    }

    @Override
    public StockOperationResult decreaseStock(StockDecreaseCommand command) {
        try {
            DecreaseFeignResponse response = remoteCallTemplate.execute(TARGET, () ->
                    hubFeignClient.decrease("order-" + command.orderId() + "-decrease",
                            new DecreaseFeignRequest(command.orderId(), command.hubId(),
                                    command.productId(), command.quantity())));
            return new StockOperationResult(response.operationId(), true, null, response.remainingQuantity());
        } catch (RemoteBusinessException e) {
            // 재고 부족 또는 그 허브에 재고 자체가 없음 = 차감 안 된 것이 확실 -> "거절" 결과 (주문 관점에서는 둘 다 재고 부족)
            if (e.getErrorCode() == InventoryErrorCode.INSUFFICIENT_STOCK
                    || e.getErrorCode() == InventoryErrorCode.INVENTORY_NOT_FOUND) {
                return new StockOperationResult(null, false, "INSUFFICIENT_STOCK", 0);
            }
            throw e;  // 멱등 충돌/입력 오류는 우리 쪽 버그 신호 -> 숨기지 않고 그대로
        } catch (RemoteServiceException | RemoteTimeoutException e) {
            throw unavailable(e);  // 결과를 모름 → service가 같은 키 재전송으로 확인 후 복원
        }
    }

    @Override
    public StockOperationResult restoreStock(StockRestoreCommand command) {
        try {
            RestoreFeignResponse response = remoteCallTemplate.execute(TARGET, () ->
                    hubFeignClient.restore("order-" + command.orderId() + "-restore",
                            new RestoreFeignRequest(command.originalOperationId())));
            return new StockOperationResult(response.operationId(), true, null, response.remainingQuantity());
        } catch (RemoteBusinessException e) {
            // 이미 복원됨 = 되돌리려던 목표가 이미 달성됨
            if (e.getErrorCode() == InventoryErrorCode.INVENTORY_ALREADY_RESTORED) {
                return new StockOperationResult(null, true, null, 0);
            }
            throw e;
        }
    }

    private BusinessException unavailable(RuntimeException cause) {
        BusinessException exception = new BusinessException(OrderErrorCode.INVENTORY_UNAVAILABLE);
        exception.initCause(cause);  // 원인은 로그 추적용으로 보존
        return exception;
    }
}
