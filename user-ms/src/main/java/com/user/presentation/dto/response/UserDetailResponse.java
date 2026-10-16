package com.user.presentation.dto.response;

import com.user.application.dto.UserResult;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 관리자용 사용자 상세 (USR-11 상세 조회, 정보 수정·역할 변경·배송담당자 지정의 응답).
 * 내 정보(MyInfoResponse)와 달리 활성 여부와 수정·삭제 일시를 보여 준다. 비밀번호 해시는 내보내지 않는다.
 */
public record UserDetailResponse(
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
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {

    public static UserDetailResponse from(UserResult result) {
        return new UserDetailResponse(result.id(), result.username(), result.name(), result.slackId(), result.role(),
                result.deliveryManager(), result.organizationType(), result.organizationName(),
                result.organizationId(), result.passwordChangeRequired(), result.active(),
                result.createdAt(), result.updatedAt(), result.deletedAt());
    }
}
