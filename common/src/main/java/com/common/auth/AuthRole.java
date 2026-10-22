package com.common.auth;

import java.util.Arrays;
import java.util.Optional;

/**
 * Gateway가 {@link AuthContract#HEADER_USER_ROLE}로 보내는 역할 값.
 * 각 서비스 domain의 역할 enum(user-ms {@code UserRole} 등)과는 별개로, 헤더 계약만 나타낸다.
 */
public enum AuthRole {
    MASTER,
    USER;

    /** 헤더 값과 이름이 정확히 같을 때만 역할로 본다 (대소문자·공백 보정 없음) */
    public static Optional<AuthRole> from(String value) {
        return Arrays.stream(values())
                .filter(role -> role.name().equals(value))
                .findFirst();
    }
}
