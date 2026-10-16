package com.user.presentation.dto.request;

import com.user.application.dto.CreateUserCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * USR-10 MASTER의 사용자 생성 요청 = 회원가입 항목 + 역할 + 배송담당 여부. 여기서는 필수값·길이·역할 값만 본다.
 * username·비밀번호·소속 유형 규칙은 회원가입처럼 domain이 검사해 구체적인 코드로 응답한다.
 * 역할·배송담당 여부를 정해서 만드는 기능이므로 둘 다 필수다 (빠뜨려서 USER·비담당자가 조용히 만들어지지 않게).
 */
public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 80) String slackId,
        @NotBlank String organizationType,
        @NotBlank @Size(max = 150) String organizationName,
        @NotNull @Pattern(regexp = "MASTER|USER", message = "MASTER 또는 USER만 가능합니다.") String role,
        @NotNull Boolean isDeliveryManager
) {

    public CreateUserCommand toCommand(UUID masterId) {
        return new CreateUserCommand(masterId, username, password, name, slackId, organizationType, organizationName,
                role, isDeliveryManager);
    }

    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username + ", password=****, role=" + role + "]";
    }
}
