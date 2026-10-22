package com.user.presentation.dto.request;

import com.user.application.dto.LoginCommand;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청. 빈 값만 400으로 막는다. 가입 규칙(ID 형식·비밀번호 길이)은 보지 않고,
 * 규칙에 맞지 않는 값도 401 INVALID_CREDENTIALS로 응답한다 (로그인에서 가입 규칙을 드러내지 않음, U05 D1).
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {

    public LoginCommand toCommand() {
        return new LoginCommand(username, password);
    }

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=****]";
    }
}
