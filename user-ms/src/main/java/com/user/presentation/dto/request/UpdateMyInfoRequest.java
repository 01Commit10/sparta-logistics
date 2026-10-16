package com.user.presentation.dto.request;

import com.user.application.dto.UpdateSlackIdCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 내 정보 수정 요청. 본인은 Slack ID만 바꿀 수 있어 다른 필드를 두지 않는다.
 * 본문에 name 등이 와도 바인딩되지 않고 무시된다 (U09 3장, 전역 fail-on-unknown-properties는 켜지 않음).
 */
public record UpdateMyInfoRequest(@NotBlank @Size(max = 80) String slackId) {

    public UpdateSlackIdCommand toCommand(UUID userId) {
        return new UpdateSlackIdCommand(userId, slackId);
    }
}
