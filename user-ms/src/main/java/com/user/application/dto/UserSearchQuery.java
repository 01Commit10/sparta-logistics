package com.user.application.dto;

/**
 * USR-11 사용자 목록 검색 조건. role·organizationType은 문자열 (값 형식은 presentation이 검사).
 * includeDeleted가 null이면 활성 사용자만 본다.
 */
public record UserSearchQuery(
        String username,
        String name,
        String role,
        Boolean deliveryManager,
        String organizationType,
        Boolean includeDeleted
) {
}
