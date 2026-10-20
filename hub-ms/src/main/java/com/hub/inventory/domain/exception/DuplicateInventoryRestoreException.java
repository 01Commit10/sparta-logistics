package com.hub.inventory.domain.exception;

// 같은 원본 차감의 복원 기록을 중복 저장했을 때, 롤백 후 처리하기 위한 내부 예외다.
public class DuplicateInventoryRestoreException extends RuntimeException {

    public DuplicateInventoryRestoreException(Throwable cause) {
        super(
                "동일한 원본 차감의 복원 기록이 이미 존재합니다.",
                cause
        );
    }
}