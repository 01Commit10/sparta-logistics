package com.user.application.dto;

import java.time.Instant;
import java.util.UUID;

/** 로그아웃할 Access Token. Gateway가 검증한 토큰의 값(X-User-Id, X-Token-Jti, X-Token-Exp)을 받는다 */
public record LogoutCommand(UUID userId, String jti, Instant expiresAt) {
}
