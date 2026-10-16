package com.delivery.application.dto.result;

import com.delivery.domain.entity.Delivery;

import java.util.UUID;

public record DeliveryCreateResult(
        UUID id
) {

    public static DeliveryCreateResult from(Delivery delivery) {
        return new DeliveryCreateResult(delivery.getId());
    }
}
