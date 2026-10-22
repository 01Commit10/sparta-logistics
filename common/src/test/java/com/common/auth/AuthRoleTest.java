package com.common.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRoleTest {

    @Test
    @DisplayName("헤더 값 MASTER·USER는 역할로 바뀐다")
    void from() {
        assertThat(AuthRole.from("MASTER")).contains(AuthRole.MASTER);
        assertThat(AuthRole.from("USER")).contains(AuthRole.USER);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ADMIN", "master", "User", " MASTER", "ROLE_MASTER"})
    @DisplayName("정의되지 않은 값, 대소문자가 다른 값, 공백이 붙은 값은 역할이 아니다")
    void fromInvalid(String value) {
        assertThat(AuthRole.from(value)).isEmpty();
    }
}
