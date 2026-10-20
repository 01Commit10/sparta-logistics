package com.order.domain.client;

import java.util.UUID;

// orderId: 복원 멱등 키(order-{orderId}-restore)를 만들 때 사용
// originalOperationId: 되돌릴 차감 작업 ID (hub가 이 기록으로 복원 수량을 정함)
public record StockRestoreCommand(UUID orderId, UUID originalOperationId) {
}