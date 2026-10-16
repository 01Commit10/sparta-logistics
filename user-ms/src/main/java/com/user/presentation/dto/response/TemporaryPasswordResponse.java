package com.user.presentation.dto.response;

import com.user.application.dto.TemporaryPasswordResult;

import java.util.UUID;

/**
 * USR-21 비밀번호 초기화 결과. 임시 비밀번호는 이 응답으로 1회만 보여 준다 (저장하지 않음, Cache-Control: no-store).
 * 누구의 임시 비밀번호인지 MASTER가 바로 확인하도록 대상 id·username을 함께 보낸다.
 */
public record TemporaryPasswordResponse(UUID userId, String username, String temporaryPassword) {

    public static TemporaryPasswordResponse from(TemporaryPasswordResult result) {
        return new TemporaryPasswordResponse(result.userId(), result.username(), result.temporaryPassword());
    }

    @Override
    public String toString() {
        return "TemporaryPasswordResponse[userId=" + userId + ", username=" + username + ", temporaryPassword=****]";
    }
}
