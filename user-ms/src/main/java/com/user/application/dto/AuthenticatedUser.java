package com.user.application.dto;

import java.util.UUID;

/**
 * ID·비밀번호 확인을 통과한 사용자. 토큰 발급(TokenService)에 필요한 값만 담는다.
 * role은 문자열로 넘겨 token 도메인이 user 도메인 enum에 의존하지 않게 한다.
 */
public record AuthenticatedUser(UUID id, String role, boolean passwordChangeRequired) {
}
