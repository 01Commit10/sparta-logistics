package com.user.application.dto;

/** 로그인 결과. 토큰 원문이 로그에 남지 않게 toString에서 가린다. */
public record LoginResult(String accessToken, String refreshToken, boolean passwordChangeRequired) {

    @Override
    public String toString() {
        return "LoginResult[accessToken=****, refreshToken=****, passwordChangeRequired=" + passwordChangeRequired + "]";
    }
}
