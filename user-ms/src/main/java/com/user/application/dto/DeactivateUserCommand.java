package com.user.application.dto;

import java.util.UUID;

/** USR-17 비활성화. masterId는 X-User-Id (삭제자로 기록) */
public record DeactivateUserCommand(UUID userId, UUID masterId) {
}
