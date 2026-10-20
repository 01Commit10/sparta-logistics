package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.OrderErrorCode;
import com.order.application.dto.OrderResult;
import com.order.domain.entity.Order;
import com.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderQueryService orderQueryService;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID OTHER_ID = UUID.randomUUID();

    private Order orderOf(UUID orderedBy) {
        Order order = Order.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                3, new BigDecimal("1000"), null, orderedBy);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }

    @Test
    @DisplayName("주문자는 자신의 주문을 단건 조회할 수 있다")
    void getOrderByOwner() {
        given(orderRepository.findActiveById(ORDER_ID)).willReturn(Optional.of(orderOf(OWNER_ID)));

        OrderResult result = orderQueryService.getOrder(ORDER_ID, OWNER_ID, false);

        assertThat(result.orderId()).isEqualTo(ORDER_ID);
    }

    @Test
    @DisplayName("MASTER는 다른 사용자의 주문도 단건 조회할 수 있다")
    void getOrderByMaster() {
        given(orderRepository.findActiveById(ORDER_ID)).willReturn(Optional.of(orderOf(OWNER_ID)));

        OrderResult result = orderQueryService.getOrder(ORDER_ID, OTHER_ID, true);

        assertThat(result.orderId()).isEqualTo(ORDER_ID);
    }

    @Test
    @DisplayName("주문자가 아닌 USER가 단건 조회하면 403 예외가 발생한다")
    void getOrderByOtherUser() {
        given(orderRepository.findActiveById(ORDER_ID)).willReturn(Optional.of(orderOf(OWNER_ID)));

        assertThatThrownBy(() -> orderQueryService.getOrder(ORDER_ID, OTHER_ID, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("없거나 삭제된 주문을 단건 조회하면 404 예외가 발생한다")
    void getOrderNotFound() {
        given(orderRepository.findActiveById(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderQueryService.getOrder(ORDER_ID, OWNER_ID, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("MASTER의 목록 조회는 삭제되지 않은 전체 주문을 조회한다")
    void getOrdersByMaster() {
        Pageable pageable = PageRequest.of(0, 10);
        given(orderRepository.findAllActive(pageable)).willReturn(new PageImpl<>(List.of(orderOf(OWNER_ID))));

        Page<OrderResult> result = orderQueryService.getOrders(OTHER_ID, true, pageable);

        assertThat(result.getContent()).hasSize(1);
        then(orderRepository).should(never()).findAllActiveByOrderedBy(any(), any());
    }

    @Test
    @DisplayName("USER의 목록 조회는 본인 주문만 조회한다")
    void getOrdersByUser() {
        Pageable pageable = PageRequest.of(0, 10);
        given(orderRepository.findAllActiveByOrderedBy(OWNER_ID, pageable))
                .willReturn(new PageImpl<>(List.of(orderOf(OWNER_ID))));

        Page<OrderResult> result = orderQueryService.getOrders(OWNER_ID, false, pageable);

        assertThat(result.getContent()).hasSize(1);
        then(orderRepository).should(never()).findAllActive(any());
    }
}
