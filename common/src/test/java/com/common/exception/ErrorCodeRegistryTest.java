package com.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 원격 code 문자열 복원 (C02 테스트 1). 유일성·등록 누락은 ErrorCodeUniquenessTest가 본다 (테스트 2).
 */
class ErrorCodeRegistryTest {

    @Test
    @DisplayName("등록된 code는 원래 enum 상수로 복원한다 (enum 종류와 상관없이)")
    void findsRegisteredCode() {
        assertThat(ErrorCodeRegistry.find("INVALID_CREDENTIALS")).containsSame(UserErrorCode.INVALID_CREDENTIALS);
        assertThat(ErrorCodeRegistry.find("TOKEN_EXPIRED")).containsSame(AuthErrorCode.TOKEN_EXPIRED);
        assertThat(ErrorCodeRegistry.find("RESOURCE_NOT_FOUND")).containsSame(CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"NO_SUCH_CODE", "invalid_credentials"})
    @DisplayName("모르는 code·빈 값·대소문자가 다른 code는 empty")
    void unknownCode(String code) {
        assertThat(ErrorCodeRegistry.find(code)).isEmpty();
    }

    @Test
    @DisplayName("all()은 등록된 모든 enum 상수를 담는다")
    void all() {
        assertThat(ErrorCodeRegistry.all())
                .contains(CommonErrorCode.INVALID_INPUT, AuthErrorCode.AUTH_UNAVAILABLE, UserErrorCode.LAST_MASTER)
                .hasSize(CommonErrorCode.values().length + AuthErrorCode.values().length + UserErrorCode.values().length);
    }
}
