package com.user.application.dto;

public enum InitialMasterResult {
    /** 활성 MASTER가 없어 새로 만들었다 */
    CREATED,
    /** 이미 활성 MASTER가 있어 아무것도 하지 않았다 */
    ALREADY_EXISTS,
    /** .env 값이 비어 있어 만들지 않았다 */
    NOT_CONFIGURED
}
