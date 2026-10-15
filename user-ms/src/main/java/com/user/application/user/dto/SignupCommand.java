package com.user.application.user.dto;

/**
 * 회원가입 입력. 형식 규칙(username, 비밀번호, 소속 유형)은 application·domain이 검사한다.
 * organizationType은 문자열로 받아 application에서 domain enum으로 바꾼다 (presentation이 domain을 모르게).
 */
public record SignupCommand(
        String username,
        String password,
        String name,
        String slackId,
        String organizationType,
        String organizationName
) {

    @Override
    public String toString() {
        return "SignupCommand[username=" + username + ", password=****]";
    }
}
