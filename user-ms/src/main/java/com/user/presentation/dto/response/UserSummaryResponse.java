package com.user.presentation.dto.response;

import com.user.application.dto.UserResult;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 관리자 목록의 사용자 한 줄 (USR-11). 목록에서 고르는 데 필요한 값만 둔다.
 * Slack ID·소속 ID·수정·삭제 작업자는 상세(UserDetailResponse)에서 본다. 비밀번호 해시는 내보내지 않는다.
 */
public record UserSummaryResponse(
        UUID id,
        String username,
        String name,
        String role,
        boolean isDeliveryManager,
        String organizationType,
        String organizationName,
        boolean active,
        LocalDateTime createdAt
) {

    public static UserSummaryResponse from(UserResult result) {
        return new UserSummaryResponse(result.id(), result.username(), result.name(), result.role(),
                result.deliveryManager(), result.organizationType(), result.organizationName(),
                result.active(), result.createdAt());
    }
}
