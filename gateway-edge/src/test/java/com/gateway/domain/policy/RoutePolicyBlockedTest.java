package com.gateway.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePolicyBlockedTest {

    private final RoutePolicy policy = RoutePolicy.builder()
            .blocked(List.of("/internal/**", "/api/v1/internal/**"))
            .build();

    @ParameterizedTest
    @ValueSource(strings = {"/internal/v1/users/1", "/api/v1/internal/x", "/internal", "/INTERNAL/v1", "/api/../internal/v1"})
    @DisplayName("내부 API 경로는 차단한다")
    void blocksInternalPaths(String path) {
        assertThat(policy.isBlocked(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users/internal-notes", "/api/v1/internals", "/api/v1/orders", "/internals/v1"})
    @DisplayName("'internal'이 들어 있어도 세그먼트가 다르면 차단하지 않는다")
    void doesNotBlockOtherPaths(String path) {
        assertThat(policy.isBlocked(path)).isFalse();
    }
}
