package com.hub.hubroute.application.dto;

import com.hub.hubroute.domain.entity.HubRoute;

import java.math.BigDecimal;
import java.util.UUID;

public record HubRouteResult(
        UUID id,
        UUID fromHubId,
        UUID toHubId,
        BigDecimal distanceKm,
        int durationMin
) {

    public static  HubRouteResult fromEntity(HubRoute hubRoute){
        return new HubRouteResult(
                        hubRoute.getId(),
                        hubRoute.getFromHubId(),
                        hubRoute.getToHubId(),
                        hubRoute.getDistanceKm(),
                        hubRoute.getDurationMin()
        );
    }
}
