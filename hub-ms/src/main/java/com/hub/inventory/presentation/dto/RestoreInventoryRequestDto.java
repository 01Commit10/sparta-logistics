package com.hub.inventory.presentation.dto;

import com.hub.inventory.application.dto.RestoreInventoryCommand;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RestoreInventoryRequestDto(
        @NotNull UUID originalOperationId
) {
    public RestoreInventoryCommand toCommand(String idempotencyKey){
        return new RestoreInventoryCommand(
                originalOperationId,
                idempotencyKey
        );
    }
}
