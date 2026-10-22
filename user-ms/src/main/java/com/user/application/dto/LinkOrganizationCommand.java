package com.user.application.dto;

import java.util.UUID;

/** USR-16 소속 연결. organizationName은 소속 유형이 바뀔 때만 필수 (null이면 기존 이름 유지) */
public record LinkOrganizationCommand(UUID userId, String organizationType, UUID organizationId, String organizationName) {
}
