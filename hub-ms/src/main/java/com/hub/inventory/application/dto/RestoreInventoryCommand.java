package com.hub.inventory.application.dto;

import java.util.UUID;

public record RestoreInventoryCommand(
        //어떤 재고차감 요청인지
        UUID originalOperationId,
        //복원 요청 중복 처리 확인 키
        String idempotencyKey
) {
}