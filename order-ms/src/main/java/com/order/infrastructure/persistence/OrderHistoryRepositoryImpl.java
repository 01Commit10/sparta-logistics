package com.order.infrastructure.persistence;

import com.order.domain.entity.OrderHistory;
import com.order.domain.repository.OrderHistoryRepository;
import com.order.domain.vo.OrderOperationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderHistoryRepositoryImpl implements OrderHistoryRepository {

    private final OrderHistoryJpaRepository orderHistoryJpaRepository;

    @Override
    public OrderHistory save(OrderHistory history) {
        return orderHistoryJpaRepository.save(history);
    }

    @Override
    public Optional<OrderHistory> findByOperationTypeAndIdempotencyKey(OrderOperationType operationType, String idempotencyKey) {
        return orderHistoryJpaRepository.findByOperationTypeAndIdempotencyKey(operationType, idempotencyKey);
    }
}