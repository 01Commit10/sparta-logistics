package com.hub.inventory.presentation.controller;

import com.common.auth.AuthContract;
import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.hub.inventory.application.service.InventoryService;
import com.hub.inventory.presentation.dto.CreateInventoryRequest;
import com.hub.inventory.presentation.dto.InventoryResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventories")
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<InventoryResponseDto> getInventories(
            @RequestHeader(AuthContract.HEADER_USER_ID) UUID userId,
            @RequestParam(value = "hubId",required = false) UUID hubId,
            @RequestParam(value = "productId",required = false) UUID productId
    ){
        return inventoryService.getInventories(hubId, productId)
                .stream()
                .map(InventoryResponseDto:: from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryResponseDto createInventory(
            @CurrentUser AuthUser authUser,
            @Valid @RequestBody CreateInventoryRequest request
    ){
        return InventoryResponseDto.from(
                    inventoryService.createInventory(
                            request.toCommand(
                                    authUser.id(),
                                    authUser.isMaster()
                            )
                    )
        );
    }

}
