package com.user.presentation.dto.response;

import com.user.application.dto.UserResult;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 내 정보 (USR-06·07). 비밀번호 해시·삭제 정보 같은 내부 값은 내보내지 않는다.
 * passwordChangeRequired는 클라이언트가 비밀번호 변경 화면으로 안내할 때 쓴다.
 */
public record MyInfoResponse(
        UUID id,
        String username,
        String name,
        String slackId,
        String role,
        boolean isDeliveryManager,
        String organizationType,
        String organizationName,
        UUID organizationId,
        boolean passwordChangeRequired,
        LocalDateTime createdAt
) {

    public static MyInfoResponse from(UserResult result) {
        return new MyInfoResponse(result.id(), result.username(), result.name(), result.slackId(), result.role(),
                result.deliveryManager(), result.organizationType(), result.organizationName(),
                result.organizationId(), result.passwordChangeRequired(), result.createdAt());
    }
}
