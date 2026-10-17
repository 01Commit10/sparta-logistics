package com.delivery.application.service;

import com.common.exception.BusinessException;
import com.common.exception.DeliveryErrorCode;
import com.delivery.application.client.UserClient;
import com.delivery.application.dto.result.DeliveryResult;
import com.delivery.domain.entity.Delivery;
import com.delivery.domain.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

        validateReadable(userId, delivery);
        return DeliveryResult.from(delivery);
    }

    public Page<DeliveryResult> getDeliveries(UUID userId, Pageable pageable) {
        Page<Delivery> deliveries = userClient.isDeliveryManager(userId)
                ? deliveryRepository.findAllByDeliveryManagerId(userId, pageable)
                : deliveryRepository.findAll(pageable);

        return deliveries.map(DeliveryResult::from);
    }

    public DeliveryResult getDeliveryByOrderId(UUID orderId, UUID userId) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        validateReadable(userId, delivery);
        return DeliveryResult.from(delivery);
    }


    // 배송 담당자는 본인이 담당한 배송만 조회 가능
    private void validateReadable(UUID userId, Delivery delivery) {
        if(!userId.equals(delivery.getDeliveryManagerId()) && userClient.isDeliveryManager(userId)) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED);
        }
    }
}
