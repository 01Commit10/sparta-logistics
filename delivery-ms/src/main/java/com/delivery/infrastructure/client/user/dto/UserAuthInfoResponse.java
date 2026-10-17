package com.delivery.infrastructure.client.user.dto;

import java.util.UUID;

public record UserAuthInfoResponse(
        UUID id,
        String name,
        String role,
        boolean isDeliveryManager,
        String slackId,
        boolean active
) {
}
