package com.user.fake;

import com.common.exception.AuthErrorCode;
import com.common.exception.BusinessException;
import com.user.domain.token.RefreshTokenClaims;
import com.user.domain.token.RefreshTokenParser;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 테스트용: 등록한 토큰 문자열만 통과시킨다. 나머지는 실제 파서처럼 REFRESH_TOKEN_INVALID */
public class FakeRefreshTokenParser implements RefreshTokenParser {

    private final Map<String, RefreshTokenClaims> valid = new HashMap<>();
    private final FakeTokenIssuer issuer;

    public FakeRefreshTokenParser() {
        this(null);
    }

    /** issuer가 발급한 Refresh도 통과시킨다 (발급 → 재발급 흐름 테스트) */
    public FakeRefreshTokenParser(FakeTokenIssuer issuer) {
        this.issuer = issuer;
    }

    public FakeRefreshTokenParser register(String token, UUID userId, String jti) {
        valid.put(token, new RefreshTokenClaims(userId, jti));
        return this;
    }

    @Override
    public RefreshTokenClaims parse(String token) {
        RefreshTokenClaims claims = valid.get(token);
        if (claims == null && issuer != null) {
            claims = issuer.refreshClaimsOf(token).orElse(null);
        }
        if (claims == null) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        return claims;
    }
}
