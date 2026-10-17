package com.company.company.presentation.dto;

import com.company.company.application.dto.UpdateCompanyCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 업체 수정(PATCH): 보낸 필드만 바꾸고, 빠진(null) 필드는 그대로 둔다.
 * 06 테이블 명세서 p_companies: name VARCHAR(150), address VARCHAR(255)
 * name·address는 보낼 경우 공백만으로 채울 수 없다 (@Pattern은 null을 통과시킨다).
 */
public record UpdateCompanyRequest(
        @Size(max = 150) @Pattern(regexp = "(?s).*\\S.*", message = "공백만으로 이루어질 수 없습니다.") String name,
        @Size(max = 255) @Pattern(regexp = "(?s).*\\S.*", message = "공백만으로 이루어질 수 없습니다.") String address,
        UUID hubId,
        UUID managerUserId
) {
    // 빈 요청({})은 바꿀 내용이 없으므로 400으로 막는다
    @AssertTrue(message = "수정할 필드를 하나 이상 보내야 합니다.")
    public boolean isAnyFieldPresent() {
        return name != null || address != null || hubId != null || managerUserId != null;
    }

    public UpdateCompanyCommand toCommand(UUID companyId, UUID actorId, boolean actorIsMaster) {
        return new UpdateCompanyCommand(companyId, name, address, hubId, managerUserId, actorId, actorIsMaster);
    }
}
