package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.OrderErrorCode;
import com.order.application.dto.OrderResult;
import com.order.domain.client.DeliveryClient;
import com.order.domain.client.HubClient;
import com.order.domain.client.StockRestoreCommand;
import com.order.domain.entity.Order;
import com.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderCancelService {

    private final OrderRepository orderRepository;
    private final DeliveryClient deliveryClient;
    private final HubClient hubClient;

    /**
     * 배송 취소(출발 전만) → 재고 복원 → 주문 CANCELLED.
     * 중간에 실패해도 각 단계가 멱등이라 같은 요청을 다시 보내면 이어서 처리된다.
     */
    @Transactional
    public OrderResult cancelOrder(UUID orderId, UUID userId, boolean isMaster) {
        Order order = orderRepository.findActiveById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!isMaster && !order.isOrderedBy(userId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        order.validateCancellable();   // 외부 호출 전에 상태부터 확인

        deliveryClient.cancelDelivery(order.getDeliveryId());
        hubClient.restoreStock(new StockRestoreCommand(order.getId(), order.getStockOperationId()));
        order.cancel();

        return OrderResult.from(order);
    }
}