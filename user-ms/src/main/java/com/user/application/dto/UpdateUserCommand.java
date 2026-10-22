package com.user.application.dto;

import java.util.UUID;

/** USR-13 MASTER의 정보 수정. null인 필드는 바꾸지 않는다 (부분 수정) */
public record UpdateUserCommand(UUID userId, String name, String slackId, String organizationType,
                                String organizationName) {
}
