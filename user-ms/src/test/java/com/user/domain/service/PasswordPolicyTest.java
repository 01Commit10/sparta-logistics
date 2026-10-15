package com.user.domain.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * password: 8~15자, 영문 대문자·소문자·숫자·특수문자 모두 포함 (발제 361행)
 */
class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Abcd123!", "Password1@long", "Aa1!Aa1!Aa1!Aa1"})
    @DisplayName("규칙을 모두 만족하면 통과한다")
    void valid(String raw) {
        assertThatCode(() -> PasswordPolicy.validate(raw)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @NullAndEmptySource
    @ValueSource(strings = {
            "Abc12!",            // 6자
            "Abcd12!",           // 7자
            "Abcd1234!Abcd123",  // 16자
            "abcd123!",          // 대문자 없음
            "ABCD123!",          // 소문자 없음
            "Abcdefg!",          // 숫자 없음
            "Abcd1234",          // 특수문자 없음
            "Abcd 123!",         // 공백
            "Abcd123!한"         // 허용되지 않은 문자
    })
    @DisplayName("하나라도 어기면 INVALID_PASSWORD")
    void invalid(String raw) {
        assertThatThrownBy(() -> PasswordPolicy.validate(raw))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_PASSWORD);
    }
}
