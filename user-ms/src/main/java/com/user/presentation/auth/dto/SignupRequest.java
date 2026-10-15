package com.user.presentation.auth.dto;

import com.user.application.user.dto.SignupCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청. 여기서는 필수값·길이만 본다.
 * username·비밀번호·소속 유형 규칙은 domain이 검사해 구체적인 코드(INVALID_USERNAME 등)로 응답한다.
 */
public record SignupRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 80) String slackId,
        @NotBlank String organizationType,
        @NotBlank @Size(max = 150) String organizationName
) {

    public SignupCommand toCommand() {
        return new SignupCommand(username, password, name, slackId, organizationType, organizationName);
    }

    @Override
    public String toString() {
        return "SignupRequest[username=" + username + ", password=****]";
    }
}
