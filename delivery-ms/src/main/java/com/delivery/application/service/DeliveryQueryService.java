package com.delivery.application.service;

import com.common.exception.BusinessException;
import com.common.exception.DeliveryErrorCode;
import com.delivery.application.dto.query.DeliverySearchQuery;
import com.delivery.application.dto.result.DeliveryResult;
import com.delivery.application.dto.result.DeliveryRouteResult;
import com.delivery.domain.entity.Delivery;
import com.delivery.domain.repository.DeliveryRepository;
import com.delivery.domain.repository.DeliverySearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DeliveryQueryService {

    private final DeliveryRepository deliveryRepository;


    public DeliveryResult getDelivery(UUID deliveryId, UUID userId, boolean isMaster) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        validateReadable(delivery, userId, isMaster);
        return DeliveryResult.from(delivery);
    }

    public Page<DeliveryResult> searchDeliveries(DeliverySearchQuery query, UUID userId, boolean isMaster, Pageable pageable) {
        DeliverySearchCondition condition = new DeliverySearchCondition(
                query.status(), query.deliveryManagerId(), isMaster ? null : userId);

        return deliveryRepository.search(condition, pageable).map(DeliveryResult::from);
    }

    public DeliveryResult getDeliveryByOrderId(UUID orderId, UUID userId, boolean isMaster) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        validateReadable(delivery, userId, isMaster);
        return DeliveryResult.from(delivery);
    }

    public List<DeliveryRouteResult> getDeliveryRoutes(UUID deliveryId, UUID userId, boolean isMaster) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        validateReadable(delivery, userId, isMaster);
        return delivery.getRoutes().stream()
                .sorted((a,b) -> a.getRouteSequence() - b.getRouteSequence())
                .map(DeliveryRouteResult::from)
                .toList();
    }


    // MASTER 또는 관계자(수령인, 공급·요청 업체 관리자, 구간 담당자)만 조회 가능
    private void validateReadable(Delivery delivery, UUID userId, boolean isMaster) {
        if(!isMaster && !delivery.isRelatedTo(userId)) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED);
        }
    }
}
