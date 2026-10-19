package com.delivery.presentation.dto.response;

import com.delivery.application.dto.result.DeliveryCreateResult;

import java.util.UUID;

public record DeliveryCreateResponse(
        UUID id
) {

    // Result -> Response
    public static DeliveryCreateResponse from(DeliveryCreateResult result) {
        return new DeliveryCreateResponse(result.id());
    }
}
