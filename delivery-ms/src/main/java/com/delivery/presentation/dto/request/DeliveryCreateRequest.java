package com.delivery.presentation.dto.request;

import com.delivery.application.dto.command.DeliveryCreateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DeliveryCreateRequest(
        @NotNull UUID orderId,
        @NotNull UUID departureHubId,
        @NotNull UUID requesterCompanyId,
        @NotNull UUID recipientUserId
) {

    // Request -> Command
    public DeliveryCreateCommand toCommand() {
        return new DeliveryCreateCommand(
                orderId,
                departureHubId,
                requesterCompanyId,
                recipientUserId);
    }
}
