package com.user.presentation.dto.request;

import com.user.application.dto.UpdateUserCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * MASTER의 정보 수정 요청 (부분 수정). 보내지 않은 필드(null)는 그대로 두고, 보낸 필드는 비어 있으면 안 된다.
 * 역할·배송담당·비밀번호·소속 ID는 전용 API로만 바꾼다 (본문에 와도 바인딩되지 않고 무시된다).
 */
public record UpdateUserRequest(
        @Pattern(regexp = NOT_BLANK, message = "비어 있을 수 없습니다.") @Size(max = 100) String name,
        @Pattern(regexp = NOT_BLANK, message = "비어 있을 수 없습니다.") @Size(max = 80) String slackId,
        @Pattern(regexp = NOT_BLANK, message = "비어 있을 수 없습니다.") String organizationType,
        @Pattern(regexp = NOT_BLANK, message = "비어 있을 수 없습니다.") @Size(max = 150) String organizationName
) {

    /** 공백이 아닌 문자가 하나 이상 (null은 @Pattern이 통과시킨다) */
    private static final String NOT_BLANK = "(?s).*\\S.*";

    /** 바꿀 필드가 하나도 없으면 400 (필드 이름 오타가 조용히 무시되지 않게) */
    @AssertTrue(message = "수정할 항목이 없습니다.")
    public boolean isAnyFieldPresent() {
        return name != null || slackId != null || organizationType != null || organizationName != null;
    }

    public UpdateUserCommand toCommand(UUID userId) {
        return new UpdateUserCommand(userId, name, slackId, organizationType, organizationName);
    }
}
