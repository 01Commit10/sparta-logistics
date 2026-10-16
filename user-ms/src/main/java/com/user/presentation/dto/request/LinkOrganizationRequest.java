package com.user.presentation.dto.request;

import com.user.application.dto.LinkOrganizationCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * USR-16 소속 연결 요청. 업체·허브만 연결할 수 있다 (본사 HQ는 연결할 조직이 없어 400).
 * 소속 유형이 바뀌면 소속명도 함께 보내야 한다 (U14와 같은 규칙, application이 검사).
 */
public record LinkOrganizationRequest(
        @NotNull @Pattern(regexp = "COMPANY|HUB", message = "COMPANY 또는 HUB만 가능합니다.") String organizationType,
        @NotNull UUID organizationId,
        @Pattern(regexp = "(?s).*\\S.*", message = "비어 있을 수 없습니다.") @Size(max = 150) String organizationName
) {

    public LinkOrganizationCommand toCommand(UUID userId) {
        return new LinkOrganizationCommand(userId, organizationType, organizationId, organizationName);
    }
}
