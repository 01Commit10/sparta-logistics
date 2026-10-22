package com.hub.hubroute.presentation.dto;

import com.hub.hubroute.application.dto.HubRouteResult;

import java.math.BigDecimal;
import java.util.UUID;

public record HubRouteResponseDto(
        UUID id,
        UUID fromHubId,
        UUID toHubId,
        BigDecimal distanceKm,
        int durationMin
) {

    public static HubRouteResponseDto from(HubRouteResult result){
        return new HubRouteResponseDto(
                result.id(),
                result.fromHubId(),
                result.toHubId(),
                result.distanceKm(),
                result.durationMin()
        );
    }
}
