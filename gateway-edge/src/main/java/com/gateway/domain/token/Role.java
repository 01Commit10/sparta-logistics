package com.gateway.domain.token;

import java.util.Arrays;
import java.util.Optional;

/** 토큰 role claim 값 (docs/plan/02 인증 계약 2.1). 대소문자까지 정확히 일치해야 한다 */
public enum Role {
    MASTER,
    USER;

    public static Optional<Role> from(String value) {
        return Arrays.stream(values()).filter(role -> role.name().equals(value)).findFirst();
    }
}
