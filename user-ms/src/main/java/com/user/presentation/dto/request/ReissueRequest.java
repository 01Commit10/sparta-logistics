package com.user.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 토큰 재발급 요청. 빈 값만 400으로 막고, 형식이 틀린 토큰은 401 REFRESH_TOKEN_INVALID. 토큰 원문은 toString에서 가린다 */
public record ReissueRequest(@NotBlank String refreshToken) {

    @Override
    public String toString() {
        return "ReissueRequest[refreshToken=****]";
    }
}
