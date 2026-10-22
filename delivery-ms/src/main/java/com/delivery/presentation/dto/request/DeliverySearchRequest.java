package com.delivery.presentation.dto.request;

import com.delivery.application.dto.query.DeliverySearchQuery;
import com.delivery.domain.vo.DeliveryStatus;

import java.util.UUID;

public record DeliverySearchRequest(
        DeliveryStatus status,
        UUID deliveryManagerId
) {

    public DeliverySearchQuery toQuery() {
        return new DeliverySearchQuery(status, deliveryManagerId);
    }
}
