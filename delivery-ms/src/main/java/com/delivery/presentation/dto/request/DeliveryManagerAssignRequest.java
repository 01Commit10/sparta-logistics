package com.delivery.presentation.dto.request;

import com.delivery.application.dto.command.DeliveryManagerAssignCommand;
import com.delivery.domain.vo.DeliveryRouteType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DeliveryManagerAssignRequest(
        @NotNull DeliveryRouteType routeType,
        @NotNull UUID deliveryManagerId
) {

    public DeliveryManagerAssignCommand toCommand() {
        return new DeliveryManagerAssignCommand(routeType, deliveryManagerId);
    }
}
