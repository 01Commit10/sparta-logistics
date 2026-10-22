package com.delivery.presentation.dto.request;

import com.delivery.application.dto.command.DeliveryRouteActualMeasurementUpdateCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record DeliveryRouteActualMeasurementUpdateRequest(
        @NotNull @PositiveOrZero BigDecimal actualDistanceKm,
        @NotNull @PositiveOrZero Integer actualDurationMin // int로 두면 null이 들어올 시 0으로 초기화됨
) {
    public DeliveryRouteActualMeasurementUpdateCommand toCommand() {
        return new DeliveryRouteActualMeasurementUpdateCommand(actualDistanceKm, actualDurationMin);
    }
}
