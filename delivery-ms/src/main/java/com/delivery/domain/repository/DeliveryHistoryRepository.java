package com.delivery.domain.repository;

import com.delivery.domain.entity.DeliveryHistory;
import com.delivery.domain.vo.DeliveryOperationType;

import java.util.Optional;

public interface DeliveryHistoryRepository {

    DeliveryHistory save(DeliveryHistory deliveryHistory);

    Optional<DeliveryHistory> findByOperationTypeAndIdempotencyKey(
            DeliveryOperationType operationType, String idempotencyKey);

}
