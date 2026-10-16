package com.gateway.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePolicyPublicTest {

    private final RoutePolicy policy = RoutePolicy.builder()
            .publicRules(List.of(
                    "POST /api/v1/auth/signup",
                    "POST /api/v1/auth/login",
                    "POST /api/v1/auth/reissue",
                    "GET /api/v1/auth/check-username"))
            .build();

    @ParameterizedTest
    @CsvSource({
            "POST, /api/v1/auth/signup",
            "POST, /api/v1/auth/login",
            "POST, /api/v1/auth/reissue",
            "GET, /api/v1/auth/check-username",
            "GET, /api/v1/auth/check-username/",
            "post, /API/v1/auth/login"
    })
    @DisplayName("공개 경로는 토큰 없이 통과한다")
    void publicPaths(String method, String path) {
        assertThat(policy.isPublic(method, path)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /api/v1/auth/logout",
            "GET, /api/v1/auth/login",
            "GET, /api/v1/auth/routing-check",
            "POST, /api/v1/auth/login/extra",
            "GET, /api/v1/users/me"
    })
    @DisplayName("같은 경로라도 메서드가 다르거나 목록에 없으면 공개가 아니다")
    void protectedPaths(String method, String path) {
        assertThat(policy.isPublic(method, path)).isFalse();
    }
}
