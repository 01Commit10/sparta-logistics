package com.delivery.infrastructure.client.hubroute.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record HubRouteResponse(
        UUID id,
        UUID departureHubId,
        UUID arrivalHubId,
        BigDecimal distanceKm,
        int durationMin
) {
}
