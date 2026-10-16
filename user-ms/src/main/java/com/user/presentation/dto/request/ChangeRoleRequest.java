package com.user.presentation.dto.request;

import com.user.application.dto.ChangeRoleCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/** USR-14 역할 변경 요청. MASTER·USER만 (대소문자 구분) */
public record ChangeRoleRequest(@NotNull @Pattern(regexp = "MASTER|USER", message = "MASTER 또는 USER만 가능합니다.") String role) {

    public ChangeRoleCommand toCommand(UUID userId) {
        return new ChangeRoleCommand(userId, role);
    }
}
