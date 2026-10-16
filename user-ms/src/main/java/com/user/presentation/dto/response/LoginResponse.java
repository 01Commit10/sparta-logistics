package com.user.presentation.dto.response;

import com.user.application.dto.LoginResult;

/** 로그인 결과 (docs/design/04 API 명세서 Auth). 토큰 원문이 로그에 남지 않게 toString에서 가린다 */
public record LoginResponse(String accessToken, String refreshToken, boolean passwordChangeRequired) {

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(result.accessToken(), result.refreshToken(), result.passwordChangeRequired());
    }

    @Override
    public String toString() {
        return "LoginResponse[accessToken=****, refreshToken=****, passwordChangeRequired=" + passwordChangeRequired + "]";
    }
}
