package com.hub.hubroute.presentation.dto;

import com.hub.hubroute.application.dto.HubRouteResult;

import java.math.BigDecimal;
import java.util.UUID;

public record HubRouteInternalResponseDto(
        UUID id,
        UUID departureHubId,
        UUID arrivalHubId,
        BigDecimal distanceKm,
        int durationMin
) {
    public static HubRouteInternalResponseDto from(HubRouteResult result) {
        return new HubRouteInternalResponseDto(
                result.id(),
                result.fromHubId(),
                result.toHubId(),
                result.distanceKm(),
                result.durationMin()
        );
    }
}
