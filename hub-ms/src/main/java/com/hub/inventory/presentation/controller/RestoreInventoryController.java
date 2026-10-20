package com.hub.inventory.presentation.controller;

import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.hub.inventory.application.service.InventoryStockService;
import com.hub.inventory.presentation.dto.RestoreInventoryRequestDto;
import com.hub.inventory.presentation.dto.RestoreInventoryResponseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/inventories")
public class RestoreInventoryController {

    private final InventoryStockService inventoryStockService;

    public RestoreInventoryController(InventoryStockService inventoryStockService) {
        this.inventoryStockService = inventoryStockService;
    }
    //복원 수량은 원본차감기록을 통해 정해지므로 요청은 원본 차감 작업 ID(originalOperationId)만 받는다.
    @PostMapping("/restore")
    public RestoreInventoryResponseDto restore(
            @RequestHeader("Idempotency-Key")
            @NotBlank
            @Size(max = 120)
            String idempotencyKey,
            @Valid @RequestBody RestoreInventoryRequestDto request,
            @CurrentUser(required = false) AuthUser authUser
    ){
        UUID actorId = (authUser == null) ? null : authUser.id();

        return RestoreInventoryResponseDto.from(
                inventoryStockService.restore(
                        request.toCommand(idempotencyKey),
                        actorId
                )
        );
    }

}
