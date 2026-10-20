package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.order.application.dto.OrderResult;
import com.order.domain.entity.Order;
import com.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderQueryService orderQueryService;

    private static final UUID ORDER_ID = UUID.randomUUID();

    @Test
    @DisplayName("주문 ID로 주문을 조회한다")
    void getOrder() {
        Order order = Order.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3,
                new BigDecimal("1000.00"), "요청사항", UUID.randomUUID());
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        OrderResult result = orderQueryService.getOrder(ORDER_ID);

        assertThat(result.orderId()).isEqualTo(ORDER_ID);
        assertThat(result.quantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("없는 주문이면 404 예외가 발생한다")
    void getOrderNotFound() {
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderQueryService.getOrder(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.RESOURCE_NOT_FOUND);
    }
}
