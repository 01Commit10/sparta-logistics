package com.user.application.dto;

import java.util.UUID;

/** USR-21 비밀번호 초기화. masterId는 X-User-Id (본인 초기화를 막는 데 쓴다) */
public record ResetPasswordCommand(UUID userId, UUID masterId) {
}
