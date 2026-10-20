package com.delivery.application.dto.command;


import java.util.UUID;

public record DeliveryCreateCommand(
        UUID orderId,
        UUID departureHubId,
        UUID requesterCompanyId,
        UUID supplierCompanyId,
        UUID recipientUserId
) {

}
