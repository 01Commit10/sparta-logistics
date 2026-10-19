package com.delivery.presentation.dto.response;

import com.delivery.application.dto.result.DeliveryResult;
import com.delivery.domain.vo.DeliveryStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record DeliveryResponse(
        UUID id,
        UUID orderId,
        DeliveryStatus status,
        UUID departureHubId,
        UUID arrivalHubId,
        String deliveryAddress,
        String recipientName,
        UUID recipientUserId,
        UUID deliveryManagerId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {


    public static DeliveryResponse from(DeliveryResult deliveryResult) {
        return new DeliveryResponse(
                deliveryResult.id(),
                deliveryResult.orderId(),
                deliveryResult.status(),
                deliveryResult.departureHubId(),
                deliveryResult.arrivalHubId(),
                deliveryResult.deliveryAddress(),
                deliveryResult.recipientName(),
                deliveryResult.recipientUserId(),
                deliveryResult.deliveryManagerId(),
                deliveryResult.createdAt(),
                deliveryResult.updatedAt());
    }
}
