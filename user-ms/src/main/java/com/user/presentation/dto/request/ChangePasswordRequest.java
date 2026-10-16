package com.user.presentation.dto.request;

import com.user.application.dto.ChangePasswordCommand;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * 비밀번호 변경 요청. 빈 값만 400으로 막는다. 새 비밀번호 규칙은 domain(PasswordPolicy)이 INVALID_PASSWORD로 판단한다 (가입과 같음).
 * 비밀번호 원문은 toString에서 가린다.
 */
public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {

    public ChangePasswordCommand toCommand(UUID userId) {
        return new ChangePasswordCommand(userId, currentPassword, newPassword);
    }

    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=****, newPassword=****]";
    }
}
