package com.delivery.presentation.dto.response;

import com.delivery.application.dto.result.DeliveryRouteResult;
import com.delivery.domain.vo.DeliveryRouteStatus;
import com.delivery.domain.vo.DeliveryRouteType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DeliveryRouteResponse(
        UUID id,
        UUID deliveryId,
        int routeSequence,
        DeliveryRouteType routeType,
        UUID departureHubId,
        UUID arrivalHubId,
        BigDecimal estimatedDistanceKm,
        int estimatedDurationMin,
        BigDecimal actualDistanceKm,
        Integer actualDurationMin,
        DeliveryRouteStatus routeStatus,
        UUID deliveryManagerId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static DeliveryRouteResponse from(DeliveryRouteResult result) {
        return new DeliveryRouteResponse(
                result.id(),
                result.deliveryId(),
                result.routeSequence(),
                result.routeType(),
                result.departureHubId(),
                result.arrivalHubId(),
                result.estimatedDistanceKm(),
                result.estimatedDurationMin(),
                result.actualDistanceKm(),
                result.actualDurationMin(),
                result.routeStatus(),
                result.deliveryManagerId(),
                result.createdAt(),
                result.updatedAt()
        );
    }

}
