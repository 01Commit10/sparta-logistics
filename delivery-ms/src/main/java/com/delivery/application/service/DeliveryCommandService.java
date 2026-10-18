package com.delivery.application.service;

import com.common.exception.BusinessException;
import com.common.exception.DeliveryErrorCode;
import com.delivery.application.client.UserClient;
import com.delivery.application.dto.command.DeliveryCreateCommand;
import com.delivery.application.dto.result.DeliveryCreateResult;
import com.delivery.application.dto.result.DeliveryResult;
import com.delivery.domain.entity.Delivery;
import com.delivery.domain.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class DeliveryCommandService {

    private final DeliveryRepository deliveryRepository;
    private final UserClient userClient;


    public DeliveryCreateResult createDelivery(DeliveryCreateCommand command) {
        Delivery delivery = Delivery.create(
                command.orderId(),
                command.departureHubId(),
                command.arrivalHubId(),
                command.deliveryAddress(),
                command.recipientName(),
                command.recipientUserId()
        );
        return DeliveryCreateResult.from(deliveryRepository.save(delivery));
    }

    public DeliveryResult assignDeliveryManager(UUID deliveryId, UUID deliveryManagerId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        if(!userClient.isDeliveryManager(deliveryManagerId)) {
            throw new BusinessException(DeliveryErrorCode.INVALID_DELIVERY_MANAGER);
        }

        delivery.assignDeliveryManager(deliveryManagerId);
        return DeliveryResult.from(delivery);
    }
}
