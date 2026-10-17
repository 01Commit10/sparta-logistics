package com.delivery.application.dto.result;

import com.delivery.domain.entity.Delivery;
import com.delivery.domain.vo.DeliveryStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record DeliveryResult(
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


    public static DeliveryResult from(Delivery delivery) {
        return new DeliveryResult(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getStatus(),
                delivery.getDepartureHubId(),
                delivery.getArrivalHubId(),
                delivery.getDeliveryAddress(),
                delivery.getRecipientName(),
                delivery.getRecipientUserId(),
                delivery.getDeliveryManagerId(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt());
    }
}
