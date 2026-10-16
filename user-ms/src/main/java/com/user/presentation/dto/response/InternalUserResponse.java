package com.user.presentation.dto.response;

import com.user.application.dto.UserResult;

import java.util.UUID;

/**
 * 서비스 간 계약 응답 (USR-18, 소비자: delivery-ms). 외부 API 응답과 섞어 쓰지 않는다.
 * 필드를 바꾸면 호출하는 서비스의 Feign DTO도 함께 바꿔야 한다.
 */
public record InternalUserResponse(
        UUID id,
        String name,
        String role,
        boolean isDeliveryManager,
        String slackId,
        boolean active
) {

    public static InternalUserResponse from(UserResult result) {
        return new InternalUserResponse(result.id(), result.name(), result.role(), result.deliveryManager(),
                result.slackId(), result.active());
    }
}
