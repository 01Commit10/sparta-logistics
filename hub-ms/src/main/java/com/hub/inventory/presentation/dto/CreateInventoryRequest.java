package com.hub.inventory.presentation.dto;


import com.hub.inventory.application.dto.CreateInventoryCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record CreateInventoryRequest(
        @NotNull UUID hubId,
        @NotNull UUID productId,
        @NotNull @PositiveOrZero Integer initialQuantity
) {

    public CreateInventoryCommand toCommand(
            UUID actorId,
            boolean actorIsMaster
    ){
        return new CreateInventoryCommand(
                hubId,
                productId,
                initialQuantity,
                actorId,
                actorIsMaster
        );
    }

}
