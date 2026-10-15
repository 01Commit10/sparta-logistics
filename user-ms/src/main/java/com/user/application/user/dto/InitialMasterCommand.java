package com.user.application.user.dto;

/** 초기 MASTER 생성에 쓰는 .env 값 (MASTER_USERNAME, MASTER_PASSWORD, MASTER_NAME, MASTER_SLACK_ID) */
public record InitialMasterCommand(String username, String password, String name, String slackId) {

    public boolean isConfigured() {
        return notBlank(username) && notBlank(password) && notBlank(name) && notBlank(slackId);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** 로그에 비밀번호가 남지 않게 한다 */
    @Override
    public String toString() {
        return "InitialMasterCommand[username=" + username + ", name=" + name + ", slackId=" + slackId + "]";
    }
}
