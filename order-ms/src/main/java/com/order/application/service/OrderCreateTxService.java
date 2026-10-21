package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.OrderErrorCode;
import com.order.application.dto.CreateOrderCommand;
import com.order.application.dto.OrderResult;
import com.order.application.event.OrderCompletedEvent;
import com.order.domain.entity.Order;
import com.order.domain.entity.OrderHistory;
import com.order.domain.repository.OrderHistoryRepository;
import com.order.domain.repository.OrderRepository;
import com.order.domain.vo.OrderOperationType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * 주문 생성의 짧은 DB 트랜잭션만 모은다. 원격 호출(client)은 의존하지 않는다.
 * 흐름(원격 호출, 보상)은 OrderCreateFacade가 조정한다.
 */
@Service
@RequiredArgsConstructor
public class OrderCreateTxService {

    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 같은 키의 이전 요청: 내용이 같고 성공했을 때만 이전 결과를 돌려준다.
     * 내용이 다르면 IDEMPOTENCY_KEY_CONFLICT, 진행 중이거나 실패했으면 PREVIOUS_REQUEST_NOT_SUCCEEDED
     */
    @Transactional(readOnly = true)
    public Optional<OrderResult> findPreviousResult(CreateOrderCommand command) {
        return orderHistoryRepository
                .findByOperationTypeAndIdempotencyKey(OrderOperationType.CREATE, command.idempotencyKey())
                .map(previous -> previousResult(previous, command));
    }

    /**
     * ① 선점: IN_PROGRESS 이력을 바로 커밋해 같은 키의 동시 요청을 원격 호출 전에 막는다.
     * 이미 같은 키가 있으면 DuplicateOrderRequestException (이 트랜잭션은 롤백)
     */
    @Transactional
    public UUID claim(CreateOrderCommand command) {
        OrderHistory history = orderHistoryRepository.saveAndFlush(OrderHistory.create(
                OrderOperationType.CREATE, command.idempotencyKey(), command.requestHash()));
        return history.getId();
    }

    // ② 주문 PROCESSING 저장 + 이력에 연결
    @Transactional
    public UUID saveOrder(CreateOrderCommand command, BigDecimal unitPrice, UUID historyId) {
        Order order = orderRepository.save(Order.create(
                command.requesterCompanyId(),
                command.supplierCompanyId(),
                command.productId(),
                command.quantity(),
                unitPrice,
                command.requestNote(),
                command.orderedBy()
        ));
        findHistory(historyId).linkOrder(order);
        return order.getId();
    }

    // ③ 주문 COMPLETED + 이력 SUCCEEDED. 알림 이벤트는 이 트랜잭션이 커밋된 뒤에 처리된다 (AFTER_COMMIT)
    @Transactional
    public OrderResult complete(UUID orderId, UUID historyId, UUID deliveryId, UUID stockOperationId) {
        Order order = findOrder(orderId);
        order.complete(deliveryId, stockOperationId);
        findHistory(historyId).succeed(201);
        eventPublisher.publishEvent(OrderCompletedEvent.from(order));
        return OrderResult.from(order);
    }

    // ③ 실패 기록. 주문을 만들기 전에 실패했으면 orderId는 null
    @Transactional
    public void fail(UUID orderId, UUID historyId, int responseCode, String lastError) {
        if (orderId != null) {
            findOrder(orderId).fail();
        }
        findHistory(historyId).fail(responseCode, lastError);
    }

    private OrderResult previousResult(OrderHistory previous, CreateOrderCommand command) {
        if (!previous.isSameReqeust(command.requestHash())) {
            throw new BusinessException(OrderErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        if (!previous.isSucceeded()) {
            throw new BusinessException(OrderErrorCode.PREVIOUS_REQUEST_NOT_SUCCEEDED);
        }
        return OrderResult.from(previous.getOrder());
    }

    private Order findOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private OrderHistory findHistory(UUID historyId) {
        return orderHistoryRepository.findById(historyId)
                .orElseThrow(() -> new IllegalStateException("선점한 주문 이력이 없습니다. historyId=" + historyId));
    }
}
