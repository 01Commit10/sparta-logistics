package com.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InventoryErrorCode implements ErrorCode{
    INVENTORY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "해당하는 재고를 찾을 수 없습니다."
    ),

    INSUFFICIENT_STOCK(
            HttpStatus.CONFLICT,
            "재고가 부족합니다."
    ),

    IDEMPOTENCY_CONFLICT(
            HttpStatus.CONFLICT,
            "같은 멱등성 키로 다른 요청 내용을 전달할 수 없습니다."
    ),

    INVENTORY_QUANTITY_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "재고 수량의 저장 한도를 초과하여 복원할 수 없습니다."
    ),

    INVENTORY_ALREADY_RESTORED(
            HttpStatus.CONFLICT,
            "이미 복원된 재고 차감입니다."
    );

    private final HttpStatus httpStatus;
    private final String message;
}
