package com.gateway.domain.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @ParameterizedTest
    @ValueSource(strings = {"MASTER", "USER"})
    @DisplayName("계약에 있는 역할만 읽는다")
    void known(String value) {
        assertThat(Role.from(value)).contains(Role.valueOf(value));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ADMIN", "master", " USER"})
    @DisplayName("없거나 계약에 없는 역할은 읽지 않는다 (TOKEN_INVALID)")
    void unknown(String value) {
        assertThat(Role.from(value)).isEmpty();
    }
}
