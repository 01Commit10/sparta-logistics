package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.OrderErrorCode;
import com.order.domain.entity.Order;
import com.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderDeleteService {
    private final OrderRepository orderRepository;

    // 없거나 이미 삭제된 주문 -> 404
    @Transactional
    public void deleteOrder(UUID orderId, UUID actorId) {
        Order order = orderRepository.findActiveById(orderId)
                .orElseThrow(()-> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        order.delete(actorId);
    }

}
