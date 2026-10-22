package com.hub.hub.application.dto;

import com.hub.hub.domain.entity.Hub;

import java.util.UUID;

public record HubResult(
        UUID id,
        String name,
        String address
) {
    public static HubResult fromEntity(Hub hub) {
        return new HubResult(
                hub.getId(),
                hub.getName(),
                hub.getAddress()
        );
    }
}
