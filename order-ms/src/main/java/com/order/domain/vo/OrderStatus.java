package com.order.domain.vo;

public enum OrderStatus {
    PROCESSING,     // 주문 생성
    COMPLETED,      // 재고 확보, 배송 생성까지 완료(실제배송완료가 아님)
    FAILED,         // 처리 실패, 보상 완료
    CANCELED       // 취소 확정, 재고 복원 완료
}
