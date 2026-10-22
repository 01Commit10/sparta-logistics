package com.user.application.dto;

import com.user.domain.entity.User;

import java.util.UUID;

/**
 * ID·비밀번호 확인(로그인)이나 Refresh 검증(재발급)을 통과한 활성 사용자. 토큰 발급(TokenService)에 필요한 값만 담는다.
 * role은 문자열로 넘겨 token 도메인이 user 도메인 enum에 의존하지 않게 한다.
 */
public record AuthenticatedUser(UUID id, String role, boolean passwordChangeRequired) {

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getRole().name(), user.isPasswordChangeRequired());
    }
}
