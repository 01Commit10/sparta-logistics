package com.delivery.application.client;

import com.delivery.domain.vo.DeliveryManagers;

import java.util.UUID;

public interface UserClient {

    boolean isDeliveryManager(UUID userId);

    DeliveryManagers pickDeliveryManagers(UUID departureHubId, UUID arrivalHubId);

}
