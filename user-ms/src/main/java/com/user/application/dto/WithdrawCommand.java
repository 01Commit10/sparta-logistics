package com.user.application.dto;

import java.util.UUID;

/** USR-09 회원탈퇴. userId는 Gateway가 넣은 X-User-Id (본인). 비밀번호 원문은 toString에서 가린다 */
public record WithdrawCommand(UUID userId, String password) {

    @Override
    public String toString() {
        return "WithdrawCommand[userId=" + userId + ", password=****]";
    }
}
