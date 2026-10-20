package com.order.domain.vo;

// 요청 처리 상태
public enum OrderOperationStatus {
    IN_PROGRESS,
    SUCCEEDED,
    FAILED;

    public boolean isCompleted() {
        return this != IN_PROGRESS;
    }
}
