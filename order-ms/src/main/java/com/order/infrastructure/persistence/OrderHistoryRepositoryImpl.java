package com.order.infrastructure.persistence;

import com.order.domain.entity.OrderHistory;
import com.order.domain.exception.DuplicateOrderRequestException;
import com.order.domain.repository.OrderHistoryRepository;
import com.order.domain.vo.OrderOperationType;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OrderHistoryRepositoryImpl implements OrderHistoryRepository {

    private static final String IDEMPOTENCY_KEY_CONSTRAINT = "uk_order_histories_operation_key";

    private final OrderHistoryJpaRepository orderHistoryJpaRepository;

    @Override
    public OrderHistory save(OrderHistory history) {
        return orderHistoryJpaRepository.save(history);
    }

    @Override
    public Optional<OrderHistory> findByOperationTypeAndIdempotencyKey(OrderOperationType operationType, String idempotencyKey) {
        return orderHistoryJpaRepository.findByOperationTypeAndIdempotencyKey(operationType, idempotencyKey);
    }

    @Override
    public Optional<OrderHistory> findById(UUID id) {
        return orderHistoryJpaRepository.findById(id);
    }

    // 바로 INSERT해서 멱등키 중복을 원격 호출 전에 확인한다 (save는 커밋 때까지 INSERT를 미룬다)
    @Override
    public OrderHistory saveAndFlush(OrderHistory history) {
        try {
            return orderHistoryJpaRepository.saveAndFlush(history);
        } catch (DataIntegrityViolationException e) {
            if (isIdempotencyKeyViolation(e)) {
                throw new DuplicateOrderRequestException(history.getIdempotencyKey());
            }
            throw e;
        }
    }

    private static boolean isIdempotencyKeyViolation(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException violation
                && IDEMPOTENCY_KEY_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
    }
}