package com.hub.inventory.domain.dto;

import java.util.UUID;

public record DecreaseResult(
        UUID inventoryId,
        int remainingQuantity,
        boolean decreased
) {
    // 차감성공 : 차감 직후 남은 재고 수량
    public static DecreaseResult succeeded(UUID inventoryId, int remainingQuantity){
        return new DecreaseResult(inventoryId, remainingQuantity, true);
    }
    // 차감 실패 : 수량 부족으로 차감하지 못하여 수량이 그대로
    public static DecreaseResult insufficient(UUID inventoryId, int remainingQuantity){
        return new DecreaseResult(inventoryId, remainingQuantity, false);
    }
}