package com.order.infrastructure.client.hub;

import com.common.exception.BusinessException;
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
            // 허브가 4xx로 거절 = 처리 안 된 것이 확실 → "거절" 결과로 변환 (복원 불필요)
            return new StockOperationResult(null, false, "INSUFFICIENT_STOCK", 0);
        } catch (RemoteServiceException | RemoteTimeoutException e) {
            throw unavailable(e);  // 결과를 모름 → service가 복원 시도
        }
    }

    @Override
    public StockOperationResult restoreStock(StockRestoreCommand command) {
        RestoreFeignResponse response = remoteCallTemplate.execute(TARGET, () ->
                hubFeignClient.restore("order-" + command.orderId() + "-restore",
                        new RestoreFeignRequest(command.orderId())));
        return new StockOperationResult(response.operationId(), true, null, response.remainingQuantity());
    }

    private BusinessException unavailable(RuntimeException cause) {
        BusinessException exception = new BusinessException(OrderErrorCode.INVENTORY_UNAVAILABLE);
        exception.initCause(cause);  // 원인은 로그 추적용으로 보존
        return exception;
    }
}
