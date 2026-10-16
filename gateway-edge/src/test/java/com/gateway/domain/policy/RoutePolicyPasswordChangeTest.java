package com.gateway.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GW-07 테스트 3: 비밀번호 변경이 필요한 사용자(pcr=true)가 호출할 수 있는 요청.
 */
class RoutePolicyPasswordChangeTest {

    /** application.yaml gateway.policy.password-change-allowed와 같은 목록 (RoutePolicyPropertiesTest가 실제 yaml과 대조한다) */
    static final List<String> PASSWORD_CHANGE_ALLOWED = List.of(
            "GET /api/v1/users/me",
            "PATCH /api/v1/users/me/password",
            "POST /api/v1/auth/logout");

    private final RoutePolicy policy = RoutePolicy.builder()
            .passwordChangeAllowedRules(PASSWORD_CHANGE_ALLOWED)
            .build();

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
            "GET, /api/v1/users/me, true",
            "PATCH, /api/v1/users/me/password, true",
            "POST, /api/v1/auth/logout, true",
            "get, /api/v1/users/me/, true",
            "PATCH, /api/v1/users/me, false",            // 내 정보 수정(Slack ID)은 비밀번호를 바꾼 뒤
            "DELETE, /api/v1/users/me, false",           // 회원탈퇴도 비밀번호를 바꾼 뒤
            "GET, /api/v1/users/me/password, false",
            "GET, /api/v1/orders, false",
            "GET, /api/v1/users, false",
            "POST, /api/v1/auth/reissue, false"         // 공개 경로라 이 판단까지 오지 않는다
    })
    @DisplayName("허용 3개(내 정보 조회, 비밀번호 변경, 로그아웃)만 true")
    void allowedOnlyThree(String method, String path, boolean expected) {
        assertThat(policy.isAllowedDuringPasswordChange(method, path)).isEqualTo(expected);
    }
}
