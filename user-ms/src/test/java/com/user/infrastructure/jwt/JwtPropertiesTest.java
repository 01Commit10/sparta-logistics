package com.user.infrastructure.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    /** 테스트 전용 키 (Base64, 41바이트) */
    static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci11c2VyLW1zLXRva2VuLWlzc3VlciE=";

    private static JwtProperties properties(String secretKey) {
        return new JwtProperties(secretKey, Duration.ofMinutes(30), Duration.ofDays(7));
    }

    @Test
    @DisplayName("Base64로 디코딩한 키 바이트를 돌려준다")
    void decodesKey() {
        assertThat(new String(properties(SECRET).secretKeyBytes())).isEqualTo("test-secret-key-for-user-ms-token-issuer!");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("키가 없으면 기동을 멈춘다")
    void missingKey(String secretKey) {
        assertThatThrownBy(() -> properties(secretKey))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JWT_SECRET_KEY");
    }

    @Test
    @DisplayName("디코딩해 32바이트 미만인 키는 기동을 멈춘다 (HS256)")
    void shortKey() {
        assertThatThrownBy(() -> properties("c2hvcnQta2V5"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("32바이트");
    }

    @Test
    @DisplayName("경계값: 32바이트 키는 받고 31바이트 키는 거부한다")
    void keyLengthBoundary() {
        String key32 = Base64.getEncoder().encodeToString("a".repeat(32).getBytes());
        String key31 = Base64.getEncoder().encodeToString("a".repeat(31).getBytes());

        assertThat(properties(key32).secretKeyBytes()).hasSize(32);
        assertThatThrownBy(() -> properties(key31)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Base64가 아닌 키는 기동을 멈추고, 메시지에 키 값을 넣지 않는다")
    void notBase64() {
        assertThatThrownBy(() -> properties("not base64 !!! value-that-is-long-enough"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base64")
                .hasMessageNotContaining("value-that-is-long-enough");
    }

    @Test
    @DisplayName("유효 시간이 없거나 0 이하면 기동을 멈춘다")
    void invalidTtl() {
        assertThatThrownBy(() -> new JwtProperties(SECRET, null, Duration.ofDays(7)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("access-ttl");
        assertThatThrownBy(() -> new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("refresh-ttl");
    }

    @Test
    @DisplayName("toString에 키 값을 넣지 않는다")
    void toStringMasksKey() {
        assertThat(properties(SECRET).toString()).doesNotContain(SECRET).contains("****");
    }
}
