package com.user.application.dto;

import java.util.UUID;

/** USR-07 내 정보 수정. userId는 Gateway가 넣은 X-User-Id (본인) */
public record UpdateSlackIdCommand(UUID userId, String slackId) {
}
