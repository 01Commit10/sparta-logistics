package com.gateway.domain.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class UserHeadersTest {

    @Test
    @DisplayName("검증된 claims로 하위 서비스에 보낼 헤더 4개를 만든다 (X-Token-Exp는 epoch 초)")
    void fromClaims() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AccessTokenClaims claims = new AccessTokenClaims(userId, Role.USER, false, "jti-1",
                Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-10-15T00:30:00Z"));

        assertThat(UserHeaders.from(claims)).containsExactly(
                entry("X-User-Id", "11111111-1111-1111-1111-111111111111"),
                entry("X-User-Role", "USER"),
                entry("X-Token-Jti", "jti-1"),
                entry("X-Token-Exp", "1792024200"));
    }
}
