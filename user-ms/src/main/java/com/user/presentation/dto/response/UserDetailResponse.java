package com.user.presentation.dto.response;

import com.user.application.dto.UserResult;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 관리자용 사용자 상세 (USR-11 상세 조회, 정보 수정·역할 변경·배송담당자 지정의 응답).
 * 내 정보(MyInfoResponse)와 달리 활성 여부와 생성·수정·삭제 일시·작업자를 보여 준다. 비밀번호 해시는 내보내지 않는다.
 * 작업자: 회원가입은 본인, 요청 밖 시스템 작업은 SYSTEM UUID(00000000-…), 탈퇴는 본인, 비활성화는 MASTER.
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
        UUID createdBy,
        LocalDateTime updatedAt,
        UUID updatedBy,
        LocalDateTime deletedAt,
        UUID deletedBy
) {

    public static UserDetailResponse from(UserResult result) {
        return new UserDetailResponse(result.id(), result.username(), result.name(), result.slackId(), result.role(),
                result.deliveryManager(), result.organizationType(), result.organizationName(),
                result.organizationId(), result.passwordChangeRequired(), result.active(),
                result.createdAt(), result.createdBy(), result.updatedAt(), result.updatedBy(),
                result.deletedAt(), result.deletedBy());
    }
}
