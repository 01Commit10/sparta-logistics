package com.delivery.application.dto.command;

import java.math.BigDecimal;

public record DeliveryRouteActualMeasurementUpdateCommand(
        BigDecimal actualDistanceKm,
        int actualDurationMin
) {
}
