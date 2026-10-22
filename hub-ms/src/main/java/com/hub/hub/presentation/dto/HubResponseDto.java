package com.hub.hub.presentation.dto;

import com.hub.hub.application.dto.HubResult;

import java.util.UUID;

public record HubResponseDto(
        UUID id,
        String name,
        String address
) {
    public static HubResponseDto from(HubResult result) {
        return new HubResponseDto(
                result.id(),
                result.name(),
                result.address()
        );
    }
}
