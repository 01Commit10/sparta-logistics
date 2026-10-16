package com.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gateway 인증 오류 코드 (docs/Common/SYS-01 게이트웨이 인증 인가 설계 6장).
 */
class AuthErrorCodeTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "TOKEN_MISSING,            UNAUTHORIZED",
            "TOKEN_EXPIRED,            UNAUTHORIZED",
            "TOKEN_INVALID,            UNAUTHORIZED",
            "TOKEN_REVOKED,            UNAUTHORIZED",
            "PASSWORD_CHANGE_REQUIRED, FORBIDDEN",
            "AUTH_UNAVAILABLE,         SERVICE_UNAVAILABLE",
    })
    @DisplayName("설계 문서의 HTTP 상태와 같다")
    void statusMatchesDesign(AuthErrorCode code, HttpStatus status) {
        assertThat(code.getHttpStatus()).isEqualTo(status);
        assertThat(code.getCode()).isEqualTo(code.name());
    }
}
