package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.OrderErrorCode;
import com.order.application.dto.OrderResult;
import com.order.domain.entity.Order;
import com.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;

    //단건: 없거나 삭제됨 -> 404, MASTER가 아니고 주문자도 아니면 ->403
    public OrderResult getOrder(UUID orderId, UUID userId, boolean isMaster) {
        Order order = orderRepository.findActiveById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!isMaster && !order.isOrderedBy(userId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        return OrderResult.from(order);
    }

    // 목록: MASTER는 전체, 그 외는 본인 주문만(삭제된 주문 제외)
    public Page<OrderResult> getOrders(UUID userId, boolean isMaster, Pageable pageable) {
        Page<Order> orders = isMaster
                ? orderRepository.findAllActive(pageable)
                : orderRepository.findAllActiveByOrderedBy(userId, pageable);
        return orders.map(OrderResult::from);
    }
}
