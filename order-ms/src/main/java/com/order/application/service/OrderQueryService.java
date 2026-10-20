package com.order.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.order.application.dto.OrderResult;
import com.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 주문 단건 조회 (부하 실험 P02용: 주문 생성과 같은 커넥션 풀을 쓰는 읽기 요청).
 * 권한 검사는 하지 않는다 (실험 브랜치 전용).
 */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public OrderResult getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(OrderResult::from)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    }
}
