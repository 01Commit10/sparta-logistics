package com.delivery.domain.vo;

import java.math.BigDecimal;

public record RouteEstimate(
        BigDecimal distanceKm,
        Integer durationMin
) {
}
