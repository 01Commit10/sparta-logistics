package com.hub.hub.presentation.dto;

import com.hub.hub.domain.entity.Hub;

import java.util.UUID;

public record HubResponse(
        UUID id,
        String name,
        String address
) {
    public static HubResponse fromEntity(Hub hub) {
        return new HubResponse(
                hub.getId(),
                hub.getName(),
                hub.getAddress()
        );
    }
}
