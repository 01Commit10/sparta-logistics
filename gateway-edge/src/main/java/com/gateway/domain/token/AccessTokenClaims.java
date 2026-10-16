package com.gateway.domain.token;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 서명·만료·종류 검증을 통과한 Access Token 값 */
public record AccessTokenClaims(
        UUID userId,
        Role role,
        boolean passwordChangeRequired,
        String jti,
        Instant issuedAt,
        Instant expiresAt
) {

    public AccessTokenClaims {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(jti, "jti");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean isMaster() {
        return role == Role.MASTER;
    }
}
