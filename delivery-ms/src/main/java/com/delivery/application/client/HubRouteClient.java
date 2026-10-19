package com.delivery.application.client;

import com.delivery.domain.vo.RouteEstimate;

import java.util.UUID;

public interface HubRouteClient {

    RouteEstimate getRouteEstimate(UUID departureHubId, UUID arrivalHubId);
}
