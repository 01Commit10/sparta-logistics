package com.delivery.domain.vo;

import java.util.UUID;

public record DeliveryManagers(
        UUID departureManagerId,
        UUID arrivalManagerId
) {
}
