package com.gateway.domain.policy;

import com.common.auth.AuthContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedHeadersTest {

    @ParameterizedTest
    @ValueSource(strings = {"X-User-Id", "x-user-role", "X-USER-ID", "X-Token-Jti", "X-Token-Exp", "X-User-Anything"})
    @DisplayName("Gateway만 넣을 수 있는 신뢰 헤더는 접두사로 판단한다 (대소문자 무관)")
    void trustedHeaders(String name) {
        assertThat(TrustedHeaders.isTrusted(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Authorization", "Idempotency-Key", "X-Request-Id", "X-Username", "Content-Type", "X-Tokens"})
    @DisplayName("그 외 헤더는 신뢰 헤더가 아니다")
    void otherHeaders(String name) {
        assertThat(TrustedHeaders.isTrusted(name)).isFalse();
    }

    @Test
    @DisplayName("Gateway가 주입하는 계약 헤더는 모두 신뢰 헤더라서 외부 요청에서 지워진다")
    void injectedHeadersAreTrusted() {
        assertThat(List.of(
                AuthContract.HEADER_USER_ID, AuthContract.HEADER_USER_ROLE,
                AuthContract.HEADER_TOKEN_JTI, AuthContract.HEADER_TOKEN_EXP))
                .allSatisfy(name -> assertThat(TrustedHeaders.isTrusted(name)).isTrue());
    }
}
