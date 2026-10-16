package com.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 모든 에러 코드 enum이 구현하는 인터페이스.
 * 에러 코드는 공통({@link CommonErrorCode}), 서비스별(예: OrderErrorCode) 모두 common 모듈의 이 패키지에서 일괄 관리.
 * 서비스 간 Feign 호출 시 원격 서비스가 내려준 code 문자열을 ErrorCode로 복원. (SYS-13 5장)
 */
public interface ErrorCode {

    HttpStatus getHttpStatus();

    String getMessage();

    // enum이 구현하면 상수명이 그대로 응답의 code 값이 됨. (예: INSUFFICIENT_STOCK)
    String name();

    default String getCode() {
        return name();
    }
}
