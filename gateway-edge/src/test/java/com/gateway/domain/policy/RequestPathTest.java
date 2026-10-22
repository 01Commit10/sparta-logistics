package com.gateway.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정책 비교용 경로. 하위 서비스(Tomcat)·라우트(PathPattern)가 디코딩한 경로와 같은 값으로 정책을 비교한다.
 */
class RequestPathTest {

    @ParameterizedTest
    @CsvSource({
            "/api/v1/users/u-1,          /api/v1/users/u-1",
            "/api/v1/%75sers/u-1,        /api/v1/users/u-1",
            "/api/v1/hub%73/h-1,         /api/v1/hubs/h-1",
            "/api/v1/%55SERS/u-1,        /api/v1/USERS/u-1",
            "/api/v1/hubs/%ED%97%88%EB%B8%8C, /api/v1/hubs/허브",
            "/api/v1/a+b,                /api/v1/a+b",
            "/api/v1/a+%62,              /api/v1/a+b",
            "/api/v1//users,             /api/v1//users",
    })
    @DisplayName("퍼센트 인코딩을 한 번 디코딩한 경로로 비교한다 (+는 공백이 아니다)")
    void decodes(String raw, String expected) {
        assertThat(RequestPath.parse(raw)).hasValueSatisfying(path -> assertThat(path.value()).isEqualTo(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/users;x/u-1",          // 매트릭스 파라미터: Tomcat은 ;뒤를 지워 /users/u-1로 처리한다
            "/api/v1/users/u-1;jsessionid=a",
            "/api/v1/%2e%2e/internal/x",    // 인코딩된 점
            "/api/v1/users/%2E",
            "/api/v1/users/a%2eb",          // 세그먼트 일부여도 인코딩된 점은 거부
            "/api/v1/users/a%2Eb",
            "/api/v1/users%2fu-1",          // 인코딩된 /
            "/api/v1/users%2Fu-1",
            "/api/v1/users%5cu-1",          // 인코딩된 \
            "/api/v1/users\\u-1",
            "/api/v1/users/%00",            // 널 문자
            "/api/v1/%2575sers/u-1",        // 이중 인코딩
            "/api/v1/users/../internal/x",  // 점 세그먼트
            "/api/v1/./users",
            "/api/v1/users/..",
            "/api/v1/users/%zz",            // 잘못된 인코딩
            "/api/v1/users/%7",
            "/api/v1/users/%",
            "/api/v1/users/%C3%28",         // 잘못된 UTF-8
    })
    @DisplayName("해석이 서비스마다 달라질 수 있는 경로는 거부한다")
    void rejects(String raw) {
        assertThat(RequestPath.parse(raw)).isEmpty();
    }

    @Test
    @DisplayName("점이 들어간 일반 세그먼트(파일명 등)는 거부하지 않는다")
    void dotInsideSegment() {
        assertThat(RequestPath.parse("/api/v1/files/a.b..c")).isPresent();
    }
}
