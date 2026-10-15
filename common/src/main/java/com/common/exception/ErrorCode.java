package com.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 모든 에러 코드 enum이 구현하는 인터페이스.
 * 공통 코드는 {@link CommonErrorCode}, 서비스별 코드는 각 서비스에서 enum으로 정의한다. (예: OrderErrorCode)
 */
public interface ErrorCode {

    HttpStatus getHttpStatus();

    String getMessage();

    // enum이 구현하면 상수명이 그대로 응답의 code 값이 된다. (예: INSUFFICIENT_STOCK)
    String name();

    default String getCode() {
        return name();
    }
}
