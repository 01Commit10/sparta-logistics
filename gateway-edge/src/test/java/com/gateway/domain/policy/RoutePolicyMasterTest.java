package com.gateway.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePolicyMasterTest {

    /** application.yaml gateway.policy와 같은 목록 (RoutePolicyPropertiesTest가 실제 yaml과 대조한다) */
    static final List<String> MASTER_EXCLUDE = List.of(
            "* /api/v1/users/me",
            "* /api/v1/users/me/**");
    static final List<String> MASTER = List.of(
            "POST,GET /api/v1/users",
            "GET,PATCH,DELETE /api/v1/users/*",
            "PATCH /api/v1/users/*/role",
            "PATCH /api/v1/users/*/delivery-manager",
            "PATCH /api/v1/users/*/organization",
            "POST /api/v1/users/*/restore",
            "POST /api/v1/users/*/password-reset",
            "PATCH /api/v1/hubs/*",
            "POST /api/v1/hub-routes",
            "PATCH,DELETE /api/v1/hub-routes/*",
            "POST /api/v1/companies",
            "DELETE /api/v1/companies/*");

    private final RoutePolicy policy = RoutePolicy.builder()
            .masterExcludeRules(MASTER_EXCLUDE)
            .masterRules(MASTER)
            .build();

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvFileSource(resources = "/policy/master-cases.csv")
    @DisplayName("정책표 전체: MASTER 전용 경로 판단")
    void policyTable(String method, String path, boolean expected) {
        assertThat(policy.requiresMaster(method, path)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/users/me/",
            "get, /api/v1/USERS/ME",
            "PATCH, /api/v1/users//me/password"
    })
    @DisplayName("me 경로는 표기가 달라도 MASTER 규칙에 걸리지 않는다")
    void meIsExcluded(String method, String path) {
        assertThat(policy.requiresMaster(method, path)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "get, /api/v1/users",
            "GET, /api/v1/users/",
            "GET, /api/v1/users?page=0&size=10",
            "DELETE, /api/v1/users/u-1/",
            "GET, /api/v1/me/../users"
    })
    @DisplayName("끝 슬래시·쿼리스트링·소문자 메서드·.. 를 정규화해서 판단한다")
    void normalizes(String method, String path) {
        assertThat(policy.requiresMaster(method, path)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"GET, /api/v1/users/me/../u-1"})
    @DisplayName("me로 시작해도 ..로 다른 사용자를 가리키면 MASTER 규칙을 적용한다")
    void dotDotEscapingMe(String method, String path) {
        assertThat(policy.requiresMaster(method, path)).isTrue();
    }
}
