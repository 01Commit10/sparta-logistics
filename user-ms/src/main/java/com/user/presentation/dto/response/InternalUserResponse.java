package com.user.presentation.dto.response;

import com.user.application.dto.UserResult;

import java.util.UUID;

/**
 * 서비스 간 계약 응답 (USR-18, 소비자: delivery-ms·order-ms·company-ms). 외부 API 응답과 섞어 쓰지 않는다.
 * 필드를 바꾸면 호출하는 서비스의 Feign DTO도 함께 바꿔야 한다. 필드 추가는 뒤에 붙인다 (받는 쪽은 모르는 필드를 무시).
 * organizationId는 본사(HQ)이거나 아직 소속 연결(USR-16) 전이면 null이다.
 */
public record InternalUserResponse(
        UUID id,
        String name,
        String role,
        boolean isDeliveryManager,
        String slackId,
        boolean active,
        String organizationType,
        UUID organizationId
) {

    public static InternalUserResponse from(UserResult result) {
        return new InternalUserResponse(result.id(), result.name(), result.role(), result.deliveryManager(),
                result.slackId(), result.active(), result.organizationType(), result.organizationId());
    }
}
