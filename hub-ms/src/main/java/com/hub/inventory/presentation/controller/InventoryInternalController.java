package com.hub.inventory.presentation.controller;

import com.common.auth.AuthContract;
import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.hub.inventory.application.service.InventoryService;
import com.hub.inventory.presentation.dto.InventoryResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/inventories")
public class InventoryInternalController {

    private final InventoryService inventoryService;

    public InventoryInternalController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    // 서비스 간 재고 조회 (order-ms 허브 선택용). 사용자 헤더 없이 공개 조회와 같은 결과를 반환한다.
    @GetMapping
    public List<InventoryResponseDto> getProductInventories(
            @RequestParam("productId") UUID productId
    ) {
        return inventoryService.getInventories(null, productId)
                .stream()
                .map(InventoryResponseDto::from)
                .toList();
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
