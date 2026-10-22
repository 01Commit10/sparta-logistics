package com.gateway.domain.token;

import com.common.exception.AuthErrorCode;

import java.util.Objects;
import java.util.Optional;

/** 토큰 검증 결과. 성공이면 claims, 실패면 401 응답에 쓸 오류 코드를 가진다 */
public final class VerificationResult {

    private final AccessTokenClaims claims;
    private final AuthErrorCode errorCode;

    private VerificationResult(AccessTokenClaims claims, AuthErrorCode errorCode) {
        this.claims = claims;
        this.errorCode = errorCode;
    }

    public static VerificationResult success(AccessTokenClaims claims) {
        return new VerificationResult(Objects.requireNonNull(claims), null);
    }

    public static VerificationResult failure(AuthErrorCode errorCode) {
        return new VerificationResult(null, Objects.requireNonNull(errorCode));
    }

    public boolean isSuccess() {
        return claims != null;
    }

    public Optional<AccessTokenClaims> claims() {
        return Optional.ofNullable(claims);
    }

    public Optional<AuthErrorCode> errorCode() {
        return Optional.ofNullable(errorCode);
    }
}
