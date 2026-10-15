package com.common.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gateway·user-ms·각 서비스가 함께 쓰는 값이라 바뀌면 서비스 간 계약이 깨진다 (docs/plan/02 인증 계약 2.1, 2.3).
 * 이 테스트가 실패하면 값을 되돌리거나, 모든 서비스와 계약 문서를 함께 바꾼다.
 */
class AuthContractTest {

    @Test
    @DisplayName("Access·Refresh Token claim 이름과 토큰 종류 값")
    void claims() {
        assertThat(AuthContract.CLAIM_ROLE).isEqualTo("role");
        assertThat(AuthContract.CLAIM_PASSWORD_CHANGE_REQUIRED).isEqualTo("pcr");
        assertThat(AuthContract.CLAIM_TOKEN_TYPE).isEqualTo("typ");
        assertThat(AuthContract.TOKEN_TYPE_ACCESS).isEqualTo("access");
        assertThat(AuthContract.TOKEN_TYPE_REFRESH).isEqualTo("refresh");
    }

    @Test
    @DisplayName("Gateway가 주입하는 사용자 헤더 이름")
    void headers() {
        assertThat(AuthContract.HEADER_USER_ID).isEqualTo("X-User-Id");
        assertThat(AuthContract.HEADER_USER_ROLE).isEqualTo("X-User-Role");
        assertThat(AuthContract.HEADER_TOKEN_JTI).isEqualTo("X-Token-Jti");
        assertThat(AuthContract.HEADER_TOKEN_EXP).isEqualTo("X-Token-Exp");
    }
}
