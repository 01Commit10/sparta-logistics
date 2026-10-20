package com.hub.inventory.presentation.controller;

import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.common.exception.BusinessException;
import com.common.exception.InventoryErrorCode;
import com.hub.inventory.application.dto.DecreaseInventoryResult;
import com.hub.inventory.application.service.InventoryStockService;
import com.hub.inventory.domain.vo.InventoryOperationStatus;
import com.hub.inventory.presentation.dto.DecreaseInventoryRequestDto;
import com.hub.inventory.presentation.dto.DecreaseInventoryResponseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

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

        DecreaseInventoryResult decreaseResult =
                inventoryStockService.decrease(
                        request.toCommand(idempotencyKey),
                        actorId
                );

        // 거절 기록이 서비스에서 트랜잭션 중 롤백되지 않도록  컨트롤러에서 예외처리 진행
        if (decreaseResult.status()== InventoryOperationStatus.REJECTED){
            InventoryErrorCode errorCode = switch (decreaseResult.rejectionReason()){
                case INVENTORY_NOT_FOUND ->
                        InventoryErrorCode.INVENTORY_NOT_FOUND;
                case INSUFFICIENT_STOCK ->
                        InventoryErrorCode.INSUFFICIENT_STOCK;
            };
             throw new BusinessException(errorCode);
        }

        return DecreaseInventoryResponseDto.from(decreaseResult);
    }
}