package com.gateway.domain.token;

import com.common.auth.AuthContract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 검증된 토큰에서 하위 서비스로 보낼 사용자 헤더를 만든다 (docs/plan/02 인증 계약 2.3) */
public final class UserHeaders {

    private UserHeaders() {
    }

    public static Map<String, String> from(AccessTokenClaims claims) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(AuthContract.HEADER_USER_ID, claims.userId().toString());
        headers.put(AuthContract.HEADER_USER_ROLE, claims.role().name());
        headers.put(AuthContract.HEADER_TOKEN_JTI, claims.jti());
        headers.put(AuthContract.HEADER_TOKEN_EXP, String.valueOf(claims.expiresAt().getEpochSecond()));
        return Collections.unmodifiableMap(headers);
    }
}
