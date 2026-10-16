package com.gateway.domain.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BearerTokenTest {

    @ParameterizedTest
    @ValueSource(strings = {"Bearer abc", "bearer abc", "BEARER abc", "Bearer  abc "})
    @DisplayName("Bearer 접두사(대소문자 무관) 뒤의 값을 꺼낸다")
    void extractsToken(String header) {
        assertThat(BearerToken.extract(header)).contains("abc");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Bearer", "Bearer ", "Bearer    ", "Basic abc", "Bearerabc", "abc"})
    @DisplayName("Bearer 형식이 아니거나 값이 비어 있으면 토큰이 없다")
    void noToken(String header) {
        assertThat(BearerToken.extract(header)).isEmpty();
    }

    @Test
    @DisplayName("토큰 값 안의 공백은 그대로 둔다 (검증에서 TOKEN_INVALID로 거른다)")
    void keepsInnerValue() {
        assertThat(BearerToken.extract("Bearer a b")).contains("a b");
    }
}
