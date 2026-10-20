package com.delivery.domain.repository;

import com.delivery.domain.vo.DeliveryStatus;

import java.util.UUID;

public record DeliverySearchCondition(
        DeliveryStatus status, // 상태 검색
        UUID deliveryManagerId, // 담당자 검색 (구간 담당자 기준)
        UUID viewerId // 관계자 제한. MASTER면 null
) {
}
