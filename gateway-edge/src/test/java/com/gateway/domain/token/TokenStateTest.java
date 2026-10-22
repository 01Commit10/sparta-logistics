package com.gateway.domain.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GW-07 테스트 1~2: 토큰 무효 판단. iat·revoked-at은 모두 epoch 초.
 */
class TokenStateTest {

    private static final Instant REVOKED_AT = Instant.parse("2026-10-16T10:00:00Z");

    private static TokenState revokedAt(Instant at) {
        return new TokenState(false, Optional.of(at), false);
    }

    @Test
    @DisplayName("상태가 없으면(로그아웃·무효화 기록 없음) 유효하다")
    void active() {
        assertThat(TokenState.active().isRevoked(REVOKED_AT)).isFalse();
    }

    @Test
    @DisplayName("blacklist에 있으면(로그아웃) 무효다")
    void blacklisted() {
        assertThat(new TokenState(true, Optional.empty(), false).isRevoked(REVOKED_AT)).isTrue();
    }

    @Test
    @DisplayName("revoked-at보다 먼저 발급된 토큰은 무효다")
    void issuedBefore() {
        assertThat(revokedAt(REVOKED_AT).isRevoked(REVOKED_AT.minusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("revoked-at과 같은 초에 발급된 토큰도 무효다 (iat <= revoked-at)")
    void issuedSameSecond() {
        assertThat(revokedAt(REVOKED_AT).isRevoked(REVOKED_AT)).isTrue();
    }

    @Test
    @DisplayName("revoked-at 다음 초에 발급된 토큰(무효화 후 다시 로그인)은 유효하다")
    void issuedAfter() {
        assertThat(revokedAt(REVOKED_AT).isRevoked(REVOKED_AT.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("revoked-at 값이 오염됐으면 판단할 수 없으므로 무효다 (fail-closed)")
    void corrupted() {
        assertThat(new TokenState(false, Optional.empty(), true).isRevoked(REVOKED_AT.plusSeconds(3600))).isTrue();
    }
}
