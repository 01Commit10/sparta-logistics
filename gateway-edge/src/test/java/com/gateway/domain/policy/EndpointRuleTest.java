package com.gateway.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EndpointRuleTest {

    @Test
    @DisplayName("\"GET,PATCH /a/*\"는 GET·PATCH만 매칭한다")
    void parsesMethods() {
        EndpointRule rule = EndpointRule.parse("GET,PATCH /api/v1/users/*");

        assertThat(rule.matches("GET", "/api/v1/users/u-1")).isTrue();
        assertThat(rule.matches("PATCH", "/api/v1/users/u-1")).isTrue();
        assertThat(rule.matches("DELETE", "/api/v1/users/u-1")).isFalse();
        assertThat(rule.matches("GET", "/api/v1/users/u-1/role")).isFalse();
    }

    @Test
    @DisplayName("메서드 '*'는 모든 메서드를 매칭한다")
    void anyMethod() {
        EndpointRule rule = EndpointRule.parse("* /api/v1/users/me/**");

        assertThat(rule.matches("GET", "/api/v1/users/me")).isTrue();
        assertThat(rule.matches("PATCH", "/api/v1/users/me/password")).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"get,/a/b", "Get,/a/b"})
    @DisplayName("요청 메서드는 대소문자를 구분하지 않는다")
    void methodCaseInsensitive(String method, String path) {
        assertThat(EndpointRule.parse("GET /a/*").matches(method, path)).isTrue();
    }

    @Test
    @DisplayName("규칙의 메서드·공백도 정리해서 읽는다")
    void trimsSpec() {
        EndpointRule rule = EndpointRule.parse("  get , patch   /a/* ");

        assertThat(rule.matches("PATCH", "/a/1")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users", "GET", "GET,", "FETCH /a", "GET,* /a", "GET a/b", " ", "GET /a/**/b"})
    @DisplayName("형식이 잘못된 규칙은 기동 시 예외로 알린다")
    void invalidSpec(String spec) {
        assertThatThrownBy(() -> EndpointRule.parse(spec))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
