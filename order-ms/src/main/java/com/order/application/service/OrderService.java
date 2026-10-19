package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.OrderErrorCode;
import com.order.application.dto.CreateOrderCommand;
import com.order.application.dto.OrderResult;
import com.order.domain.client.*;
import com.order.domain.entity.Order;
import com.order.domain.entity.OrderHistory;
import com.order.domain.repository.OrderHistoryRepository;
import com.order.domain.repository.OrderRepository;
import com.order.domain.vo.OrderOperationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final HubClient hubClient;
    private final DeliveryClient deliveryClient;

    @Transactional
    public OrderResult createOrder(CreateOrderCommand command) {
        // 0. 같은 키로 처리한 이력이 있으면 이전 결과 반환 (멱등성)
        Optional<OrderHistory> previous = orderHistoryRepository
                .findByOperationTypeAndIdempotencyKey(OrderOperationType.CREATE, command.idempotencyKey());
        if (previous.isPresent()) {
            OrderHistory previousHistory = previous.get();
            // 같은 키인테 내용이 다르면 키 재사용으로 보고 거절
            if (!previousHistory.isSameReqeust(command.requestHash())) {
                throw new BusinessException(OrderErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            return OrderResult.from(previous.get().getOrder());
        }

        // 1. 이력 생성(IN_PROCESSING) ← 번호표: 중복 차단 + 이후 모든 성공/실패를 기록할 자리 확보
        OrderHistory history = OrderHistory.create(
                OrderOperationType.CREATE, command.idempotencyKey(), command.requestHash());
        orderHistoryRepository.save(history);

        // 2. 재고 조회 -> 차감 할 허브 선택
        InventoryInfo inventoryInfo = selectInventory(command);

        // 3. 주문 저장 (PROCESSING)
        Order order = (Order) orderRepository.save(Order.create(
                command.requesterCompanyId(),
                command.supplierCompanyId(),
                command.productId(),
                command.quantity(),
                command.unitPrice(),
                command.requestNote(),
                command.orderedBy()
        ));
        history.linkOrder(order);

        // 4. 재고 차감
        hubClient.decreaseStock(new StockDecreaseCommand(
                order.getId(), inventoryInfo.hubId(), command.productId(), command.quantity()));

        // 5. 배송 생성
        DeliveryResult delivery = deliveryClient.createDelivery(new CreateDeliveryCommand(
                order.getId(),
                inventoryInfo.hubId(),
                command.supplierCompanyId(),
                command.requesterCompanyId(),
                command.orderedBy()
        ));

        // 6. 주문 완료 처리(COMPLETED) (이력에 결과 기록)
        order.complete(delivery.deliveryId());
        history.succeed(201);

        return OrderResult.from(order);
    }

    private InventoryInfo selectInventory(CreateOrderCommand command) {
        return hubClient.findInventories(command.productId()).stream()
                .filter(inventoryInfo -> inventoryInfo.hasEnough(command.quantity()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK));
    }


}
