package com.user.application.dto;

/** 새로 발급한 Access·Refresh Token. 토큰 원문이 로그에 남지 않게 toString에서 가린다. */
public record TokenPairResult(String accessToken, String refreshToken) {

    @Override
    public String toString() {
        return "TokenPairResult[accessToken=****, refreshToken=****]";
    }
}
