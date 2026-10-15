package com.user.application.user.dto;

import com.user.domain.user.User;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 사용자 조회·변경 결과. User 엔티티를 밖으로 내보내지 않기 위해 application에서 값만 옮긴다.
 * 비밀번호 해시는 담지 않는다.
 */
public record UserResult(
        UUID id,
        String username,
        String name,
        String slackId,
        String role,
        boolean deliveryManager,
        String organizationType,
        String organizationName,
        UUID organizationId,
        boolean passwordChangeRequired,
        boolean active,
        LocalDateTime createdAt
) {

    public static UserResult from(User user) {
        return new UserResult(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getSlackId(),
                user.getRole().name(),
                user.isDeliveryManager(),
                user.getOrganizationType().name(),
                user.getOrganizationName(),
                user.getOrganizationId(),
                user.isPasswordChangeRequired(),
                user.isActive(),
                user.getCreatedAt());
    }
}
