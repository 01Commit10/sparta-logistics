package com.delivery.application.dto.query;

import com.delivery.domain.vo.DeliveryStatus;

import java.util.UUID;

public record DeliverySearchQuery(
        DeliveryStatus status,
        UUID deliveryManagerId
) {
}
