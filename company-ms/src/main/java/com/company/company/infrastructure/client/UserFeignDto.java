package com.company.company.infrastructure.client;

import java.util.UUID;

/**
 * user-ms InternalUserResponse 계약. 그쪽 필드가 바뀌면 함께 바꾼다.
 */
record UserFeignDto(
        UUID id,
        String name,
        String role,
        boolean isDeliveryManager,
        String slackId,
        boolean active
) {
}
