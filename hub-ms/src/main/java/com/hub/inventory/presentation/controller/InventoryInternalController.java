package com.hub.inventory.presentation.controller;

import com.common.auth.AuthContract;
import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.hub.inventory.application.service.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/inventories")
public class InventoryInternalController {

    private final InventoryService inventoryService;

    public InventoryInternalController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @DeleteMapping("/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProductInventories(
            @PathVariable("productId") UUID productId,
            @CurrentUser(required = false) AuthUser authUser
    ) {
        UUID actorId = authUser == null ? AuthContract.SYSTEM_USER_ID : authUser.id();
        inventoryService.deleteInventoriesByProductId(productId, actorId);
    }
}
