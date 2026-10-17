package com.user.application.dto;

import com.user.domain.token.RefreshTokenClaims;

import java.time.Instant;

/**
 * 저장된 jti와 맞는 Refresh Token과, 저장값을 확인하기 전에 읽은 revoked-at (재발급 도중 무효화 확인용).
 */
public record VerifiedRefresh(RefreshTokenClaims claims, Instant revocationSnapshot) {
}
