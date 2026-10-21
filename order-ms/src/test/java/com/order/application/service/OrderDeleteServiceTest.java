package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.OrderErrorCode;
import com.order.domain.entity.Order;
import com.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class OrderDeleteServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderDeleteService orderDeleteService;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID MASTER_ID = UUID.randomUUID();

    void deleteOrder() {
        Order order = Order.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                3, new BigDecimal("1000"), null, UUID.randomUUID());
        order.fail();

        given(orderRepository.findActiveById(ORDER_ID)).willReturn(Optional.of(order));

        assertThat(order.isDeleted()).isTrue();
        assertThat(order.getDeletedBy()).isEqualTo(MASTER_ID);
    }

    @Test
    @DisplayName("없거나 이미 삭제된 주문을 삭제하면 404 예외가 발생한다.")
    void deleteOrderNotFound() {
        given(orderRepository.findActiveById(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderDeleteService.deleteOrder(ORDER_ID, MASTER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_FOUND);
    }
}
