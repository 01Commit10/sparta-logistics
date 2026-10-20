package com.order.domain.repository;

import com.order.domain.entity.OrderHistory;
import com.order.domain.vo.OrderOperationType;

import java.util.Optional;

/* 도메인은 JPA 모른다 */
public interface OrderHistoryRepository {

    OrderHistory save(OrderHistory history);

    Optional<OrderHistory> findByOperationTypeAndIdempotencyKey(OrderOperationType operationType, String idempotencyKey);
}