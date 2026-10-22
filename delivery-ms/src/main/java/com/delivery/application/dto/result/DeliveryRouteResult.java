package com.delivery.application.dto.result;

import com.delivery.domain.entity.DeliveryRoute;
import com.delivery.domain.vo.DeliveryRouteStatus;
import com.delivery.domain.vo.DeliveryRouteType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DeliveryRouteResult(
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

    public static DeliveryRouteResult from(DeliveryRoute route) {
        return new DeliveryRouteResult(
                route.getId(),
                route.getDelivery().getId(),
                route.getRouteSequence(),
                route.getRouteType(),
                route.getDepartureHubId(),
                route.getArrivalHubId(),
                route.getEstimatedDistanceKm(),
                route.getEstimatedDurationMin(),
                route.getActualDistanceKm(),
                route.getActualDurationMin(),
                route.getRouteStatus(),
                route.getDeliveryManagerId(),
                route.getCreatedAt(),
                route.getUpdatedAt()
        );
    }
}
