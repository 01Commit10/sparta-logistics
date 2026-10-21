package com.order.infrastructure.client.user;

import java.util.UUID;

// user-ms 응답 중 필요한 필드만 (나머지는 무시)
public record UserFeignResponse(UUID id, String slackId) {
}
