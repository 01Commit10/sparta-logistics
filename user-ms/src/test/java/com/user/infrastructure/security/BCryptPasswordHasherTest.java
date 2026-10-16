package com.user.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    @DisplayName("원문과 다른 BCrypt 해시를 만들고, 같은 원문이면 일치한다")
    void hashAndMatch() {
        String hash = hasher.hash("Master!2345");

        assertThat(hash).isNotEqualTo("Master!2345").startsWith("$2");
        assertThat(hasher.matches("Master!2345", hash)).isTrue();
        assertThat(hasher.matches("Master!2346", hash)).isFalse();
    }

    @Test
    @DisplayName("같은 원문이라도 해시는 매번 다르다 (salt)")
    void saltedHash() {
        assertThat(hasher.hash("Master!2345")).isNotEqualTo(hasher.hash("Master!2345"));
    }

    @Test
    @DisplayName("BCrypt 한도(72바이트)를 넘는 비밀번호는 예외 없이 불일치로 본다 (로그인에서 500 방지)")
    void tooLongPasswordDoesNotMatch() {
        String hash = hasher.hash("Master!2345");

        assertThat(hasher.matches("a".repeat(73), hash)).isFalse();
        assertThat(hasher.matches("가".repeat(25), hash)).isFalse();   // 25자지만 UTF-8로 75바이트
    }
}
