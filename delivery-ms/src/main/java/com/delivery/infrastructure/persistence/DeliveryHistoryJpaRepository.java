package com.delivery.infrastructure.persistence;

import com.delivery.domain.entity.DeliveryHistory;
import com.delivery.domain.vo.DeliveryOperationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryHistoryJpaRepository extends JpaRepository<DeliveryHistory, UUID> {

    Optional<DeliveryHistory> findByOperationTypeAndIdempotencyKey(
            DeliveryOperationType operationType, String idempotencyKey);

}
