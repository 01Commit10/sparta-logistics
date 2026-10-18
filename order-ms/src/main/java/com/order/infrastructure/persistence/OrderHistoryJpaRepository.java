package com.order.infrastructure.persistence;

import com.order.domain.entity.OrderHistory;
import com.order.domain.vo.OrderOperationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderHistoryJpaRepository extends JpaRepository<OrderHistory, UUID> {

    Optional<OrderHistory> findByOperationTypeAndIdempotencyKey(OrderOperationType operationType, String idempotencyKey);
}