package com.delivery.presentation.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DeliveryManagerAssignRequest(
        @NotNull UUID deliveryManagerId
) {
}
