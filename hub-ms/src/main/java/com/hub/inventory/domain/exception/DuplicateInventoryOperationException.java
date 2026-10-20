package com.hub.inventory.domain.exception;
//허브ms 내부에서 중복충돌 감지하고 복구하기 위한 예외
public class DuplicateInventoryOperationException extends RuntimeException {

    public DuplicateInventoryOperationException(Throwable cause) {
        super(
                "동일한 멱등성 키의 재고 작업이 이미 존재합니다.",
                cause
        );
    }
}