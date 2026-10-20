package com.order.infrastructure.persistence;

import com.order.domain.entity.OrderHistory;
import com.order.domain.exception.DuplicateOrderRequestException;
import com.order.domain.vo.OrderOperationType;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderHistoryRepositoryImplTest {

    @Mock
    private OrderHistoryJpaRepository orderHistoryJpaRepository;

    @InjectMocks
    private OrderHistoryRepositoryImpl orderHistoryRepository;

    private final OrderHistory history =
            OrderHistory.create(OrderOperationType.CREATE, "order-req-0001", "a".repeat(64));

    @Test
    @DisplayName("멱등키 유니크 제약 위반은 중복 주문 요청 예외로 바꾼다")
    void saveAndFlushTranslatesIdempotencyKeyViolation() {
        given(orderHistoryJpaRepository.saveAndFlush(history))
                .willThrow(violation("uk_order_histories_operation_key"));

        assertThatThrownBy(() -> orderHistoryRepository.saveAndFlush(history))
                .isInstanceOf(DuplicateOrderRequestException.class);
    }

    @Test
    @DisplayName("다른 제약 위반은 그대로 던진다")
    void saveAndFlushRethrowsOtherViolations() {
        given(orderHistoryJpaRepository.saveAndFlush(history))
                .willThrow(violation("p_order_histories_order_id_fkey"));

        assertThatThrownBy(() -> orderHistoryRepository.saveAndFlush(history))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("duplicate key", new SQLException("23505"), constraintName));
    }
}
