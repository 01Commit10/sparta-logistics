package com.user.domain.token;

import java.util.UUID;

/**
 * Access Token에 담을 사용자 정보 (docs/plan/02 인증 계약 2.1 sub·role·pcr).
 * role은 문자열이다. token 도메인이 user 도메인(UserRole)에 의존하지 않게 한다.
 */
public record AccessTokenSpec(UUID userId, String role, boolean passwordChangeRequired) {
}
