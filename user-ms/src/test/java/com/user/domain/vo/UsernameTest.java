package com.user.domain.vo;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * username: 4~10자, 영문 소문자(a~z)·숫자(0~9) (발제 360행)
 */
class UsernameTest {

    @ParameterizedTest
    @ValueSource(strings = {"abcd", "user123456", "1234", "a1b2c3"})
    @DisplayName("4~10자의 소문자·숫자는 사용할 수 있다")
    void valid(String value) {
        assertThat(Username.of(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "abcdefghijk", "Abcd", "ab_cd", "ab cd", "한글아이디", "abcd!"})
    @DisplayName("길이·문자 규칙을 어기면 INVALID_USERNAME")
    void invalid(String value) {
        assertThatThrownBy(() -> Username.of(value))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_USERNAME);
    }

    @Test
    @DisplayName("값이 같으면 같은 Username이다")
    void equality() {
        assertThat(Username.of("abcd")).isEqualTo(Username.of("abcd"));
    }
}
