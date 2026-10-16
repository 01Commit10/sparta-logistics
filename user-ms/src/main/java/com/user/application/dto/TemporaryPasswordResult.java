package com.user.application.dto;

import java.util.UUID;

/** USR-21 초기화 결과. 임시 비밀번호 원문은 응답으로 1회만 나가고 저장·로그하지 않는다 (toString에서 가린다) */
public record TemporaryPasswordResult(UUID userId, String username, String temporaryPassword) {

    @Override
    public String toString() {
        return "TemporaryPasswordResult[userId=" + userId + ", username=" + username + ", temporaryPassword=****]";
    }
}
