package com.delivery.domain.vo;

import java.util.UUID;

public record DeliveryDestination(
    UUID arrivalHubId,
    String deliveryAddress
) {
}
