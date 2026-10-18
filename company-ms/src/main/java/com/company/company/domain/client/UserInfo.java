package com.company.company.domain.client;

import java.util.UUID;

/**
 * company-ms가 쓰는 사용자 정보. user-ms 응답 중 필요한 값만 둔다.
 */
public record UserInfo(UUID id, String role) {
}
