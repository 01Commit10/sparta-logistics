package com.company.company.infrastructure.client;

import java.util.UUID;

/**
 * hub-ms HubResponse 계약. 그쪽 필드가 바뀌면 함께 바꾼다.
 */
record HubFeignDto(
        UUID id,
        String name,
        String address
) {
}
