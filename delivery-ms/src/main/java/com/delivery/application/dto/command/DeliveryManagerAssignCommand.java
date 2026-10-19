package com.delivery.application.dto.command;

import com.delivery.domain.vo.DeliveryRouteType;

import java.util.UUID;

public record DeliveryManagerAssignCommand(
        DeliveryRouteType routeType,
        UUID deliveryManagerId
) {
}
