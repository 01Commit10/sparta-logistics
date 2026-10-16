package com.user.application.dto;

import java.util.UUID;

/** USR-08 비밀번호 변경. userId는 Gateway가 넣은 X-User-Id (본인). 비밀번호 원문은 toString에서 가린다 */
public record ChangePasswordCommand(UUID userId, String currentPassword, String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordCommand[userId=" + userId + ", currentPassword=****, newPassword=****]";
    }
}
