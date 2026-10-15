package com.gateway.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 경로 패턴은 세그먼트 단위로 비교한다. '*'는 세그먼트 하나, 마지막 '**'는 0개 이상.
 */
class PathRuleTest {

    @ParameterizedTest(name = "{0} ~ {1} → {2}")
    @CsvSource({
            "/internal/**,          /internal,                  true",
            "/internal/**,          /internal/v1/users/1,       true",
            "/internal/**,          /internals/v1,              false",
            "/internal/**,          /api/v1/internal/x,         false",
            "/api/v1/users,         /api/v1/users,              true",
            "/api/v1/users,         /api/v1/users/me,           false",
            "/api/v1/users/*,       /api/v1/users/u-1,          true",
            "/api/v1/users/*,       /api/v1/users,              false",
            "/api/v1/users/*,       /api/v1/users/u-1/role,     false",
            "/api/v1/users/*/role,  /api/v1/users/u-1/role,     true",
    })
    @DisplayName("세그먼트 단위로 매칭한다")
    void matchesBySegment(String pattern, String path, boolean expected) {
        assertThat(PathRule.of(pattern).matches(path)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource({
            "/api/v1/users,  /api/v1/users/",
            "/api/v1/users,  //api/v1//users",
            "/internal/**,   /api/../internal/v1",
            "/internal/**,   /INTERNAL/v1",
            "/internal/**,   /./internal/v1",
    })
    @DisplayName("끝 슬래시·중복 슬래시·'.'·'..'·대소문자 차이는 정규화한 뒤 비교한다")
    void normalizesBeforeMatching(String pattern, String path) {
        assertThat(PathRule.of(pattern).matches(path)).isTrue();
    }

    @Test
    @DisplayName("'**'는 패턴 마지막에만 쓸 수 있다")
    void doubleWildcard_onlyAtEnd() {
        assertThatThrownBy(() -> PathRule.of("/api/**/users")).isInstanceOf(IllegalArgumentException.class);
    }
}
