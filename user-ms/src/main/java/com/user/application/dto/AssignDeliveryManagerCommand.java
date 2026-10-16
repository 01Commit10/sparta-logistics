package com.user.application.dto;

import java.util.UUID;

/** USR-15 배송담당자 지정·해제. userId는 대상 사용자 (수정자 MASTER는 Auditing이 X-User-Id로 기록) */
public record AssignDeliveryManagerCommand(UUID userId, boolean deliveryManager) {
}
