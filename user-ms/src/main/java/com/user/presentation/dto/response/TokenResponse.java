package com.user.presentation.dto.response;

import com.user.application.dto.TokenPairResult;

/** 재발급 결과 (docs/design/04 API 명세서 Auth). 토큰 원문이 로그에 남지 않게 toString에서 가린다 */
public record TokenResponse(String accessToken, String refreshToken) {

    public static TokenResponse from(TokenPairResult result) {
        return new TokenResponse(result.accessToken(), result.refreshToken());
    }

    @Override
    public String toString() {
        return "TokenResponse[accessToken=****, refreshToken=****]";
    }
}
