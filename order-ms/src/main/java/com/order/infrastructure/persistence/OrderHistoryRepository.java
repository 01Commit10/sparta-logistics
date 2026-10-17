package com.order.infrastructure.persistence;

import com.order.domain.entity.OrderHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.OperationType;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderHistoryRepository implements com.order.domain.repository.OrderHistoryRepository {

    private final OrderHistoryJpaRepository orderHistoryJpaRepository;

    @Override
    public OrderHistory save(OrderHistory history) {
        return orderHistoryJpaRepository.save(history);
    }

    @Override
    public Optional<OrderHistory> findByOperationTypeAndIdempotencyKey(OperationType operationType) {
        return Optional.empty();
    }
}
