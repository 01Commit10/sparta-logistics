package com.gateway.application.request;

import com.gateway.domain.policy.RoutePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestGuardTest {

    private final RequestGuard guard =
            new RequestGuard(RoutePolicy.builder().blocked(List.of("/internal/**")).build());

    @Test
    @DisplayName("차단 경로 판단을 경로 정책에 맡긴다")
    void isBlocked() {
        assertThat(guard.isBlocked("/internal/v1/users/1")).isTrue();
        assertThat(guard.isBlocked("/api/v1/users/me")).isFalse();
    }

    @Test
    @DisplayName("신뢰 헤더 판단을 도메인 규칙에 맡긴다")
    void isTrustedHeader() {
        assertThat(guard.isTrustedHeader("X-User-Id")).isTrue();
        assertThat(guard.isTrustedHeader("Authorization")).isFalse();
    }
}
