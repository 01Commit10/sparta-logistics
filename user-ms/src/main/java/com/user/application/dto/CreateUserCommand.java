package com.user.application.dto;

import java.util.UUID;

/**
 * USR-10 MASTER의 사용자 생성. 가입 항목에 역할·배송담당 여부를 더한다.
 * organizationType·role은 문자열로 받아 application에서 domain enum으로 바꾼다 (presentation이 domain을 모르게).
 */
public record CreateUserCommand(
        UUID masterId,
        String username,
        String password,
        String name,
        String slackId,
        String organizationType,
        String organizationName,
        String role,
        boolean deliveryManager
) {

    @Override
    public String toString() {
        return "CreateUserCommand[masterId=" + masterId + ", username=" + username + ", password=****, role=" + role
                + ", deliveryManager=" + deliveryManager + "]";
    }
}
