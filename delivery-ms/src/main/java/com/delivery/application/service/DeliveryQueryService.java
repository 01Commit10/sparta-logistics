package com.delivery.application.service;

import com.common.exception.BusinessException;
import com.common.exception.DeliveryErrorCode;
import com.delivery.application.client.UserClient;
import com.delivery.application.dto.result.DeliveryResult;
import com.delivery.domain.entity.Delivery;
import com.delivery.domain.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DeliveryQueryService {

    private final DeliveryRepository deliveryRepository;
    private final UserClient userClient;


    public DeliveryResult getDelivery(UUID deliveryId, UUID userId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        if(!userId.equals(delivery.getDeliveryManagerId()) && userClient.isDeliveryManager(userId)) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED);
        }

        return DeliveryResult.from(delivery);
    }
}
