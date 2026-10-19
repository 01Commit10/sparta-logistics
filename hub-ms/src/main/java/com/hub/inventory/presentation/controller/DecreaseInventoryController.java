package com.hub.inventory.presentation.controller;

import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.hub.inventory.application.dto.DecreaseInventoryResult;
import com.hub.inventory.application.service.InventoryStockService;
import com.hub.inventory.presentation.dto.DecreaseInventoryRequestDto;
import com.hub.inventory.presentation.dto.DecreaseInventoryResponseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/inventories")
public class DecreaseInventoryController {
    private final InventoryStockService  inventoryStockService;

    public DecreaseInventoryController(InventoryStockService inventoryStockService) {
        this.inventoryStockService = inventoryStockService;
    }

    @PostMapping("/decrease")
    public DecreaseInventoryResponseDto decrease(
            @RequestHeader("Idempotency-Key")
            @NotBlank
            @Size(max = 120)
            String idempotencyKey,
            @Valid @RequestBody DecreaseInventoryRequestDto request,
            @CurrentUser(required = false) AuthUser authUser
    ){
        UUID actorId = (authUser == null) ? null : authUser.id();

        Optional<DecreaseInventoryResult> decreaseResult =
                inventoryStockService.decrease(
                        request.toCommand(idempotencyKey),
                        actorId
                );

        DecreaseInventoryResult result = decreaseResult.orElseThrow(
                ()-> new BusinessException(
                        CommonErrorCode.INVALID_INPUT,
                        "요청한 재고 차감을 적용할 수 없습니다."
                )
        );
        return DecreaseInventoryResponseDto.from(result);
    }
}
