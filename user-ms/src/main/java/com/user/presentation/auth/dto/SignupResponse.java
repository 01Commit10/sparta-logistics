package com.user.presentation.auth.dto;

import com.user.application.user.dto.UserResult;

import java.util.UUID;

/** 회원가입 결과. 비밀번호 관련 값은 내보내지 않는다 */
public record SignupResponse(
        UUID id,
        String username,
        String name,
        String slackId,
        String role,
        String organizationType,
        String organizationName
) {

    public static SignupResponse from(UserResult result) {
        return new SignupResponse(result.id(), result.username(), result.name(), result.slackId(),
                result.role(), result.organizationType(), result.organizationName());
    }
}
