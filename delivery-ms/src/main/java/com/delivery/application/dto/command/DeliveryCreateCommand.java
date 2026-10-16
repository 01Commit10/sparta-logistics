package com.delivery.application.dto.command;


import java.util.UUID;

public record DeliveryCreateCommand(
        UUID orderId,
        UUID departureHubId,
        UUID arrivalHubId,
        String deliveryAddress,
        String recipientName,
        UUID recipientUserId
) {

}
