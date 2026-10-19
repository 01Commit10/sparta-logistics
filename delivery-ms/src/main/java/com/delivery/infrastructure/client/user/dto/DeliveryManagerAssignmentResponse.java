package com.delivery.infrastructure.client.user.dto;

import java.util.UUID;

public record DeliveryManagerAssignmentResponse(
        UUID departureManagerId,
        UUID arrivalManagerId
) {
}
