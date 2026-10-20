package com.order.infrastructure.client.delivery;

import com.common.exception.BusinessException;
import com.common.exception.OrderErrorCode;
import com.common.remote.RemoteCallTemplate;
import com.order.domain.client.CreateDeliveryCommand;
import com.order.domain.client.DeliveryClient;
import com.order.domain.client.DeliveryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 재고 복원 + FAILED 처리가 같아 하나의 로직으로 구현 */
@Component
@Profile("!stub")
@RequiredArgsConstructor
public class DeliveryClientImpl implements DeliveryClient {

    private static final String TARGET = "delivery-ms";

    private final DeliveryFeignClient deliveryFeignClient;
    private final RemoteCallTemplate remoteCallTemplate;

    @Override
    public DeliveryResult createDelivery(CreateDeliveryCommand command) {
        try {
            DeliveryFeignResponse response = remoteCallTemplate.execute(TARGET, () ->
                    deliveryFeignClient.createDelivery("order-" + command.orderId() + "-delivery",
                            new DeliveryFeignRequest(command.orderId(), command.departureHubId(),
                                    command.supplierCompanyId(), command.requesterCompanyId(),
                                    command.recipientUserId())));
            return new DeliveryResult(response.id());
        } catch (BusinessException e) {  // Remote 예외 3종 모두 BusinessException
            BusinessException exception = new BusinessException(OrderErrorCode.DELIVERY_FAILED);
            exception.initCause(e);
            throw exception;
        }
    }
}
