package com.order.domain.repository;

import com.order.domain.entity.OrderHistory;
import com.order.domain.vo.OrderOperationType;

import java.util.Optional;
import java.util.UUID;

/* 도메인은 JPA 모른다 */
public interface OrderHistoryRepository {

    OrderHistory save(OrderHistory history);

    Optional<OrderHistory> findByOperationTypeAndIdempotencyKey(OrderOperationType operationType, String idempotencyKey);

    /** 바로 INSERT한다. 같은 멱등키가 이미 있으면 DuplicateOrderRequestException */
    OrderHistory saveAndFlush(OrderHistory history);

    Optional<OrderHistory> findById(UUID id);
}
