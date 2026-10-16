package com.user.application.dto;

/** 역할 변경 결과. changed=false(같은 역할)면 토큰을 무효화하지 않는다 */
public record RoleChangeResult(UserResult user, boolean changed) {
}
