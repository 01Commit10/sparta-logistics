package com.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 업체(company-ms) 오류 코드 (docs/design/04 API 명세서 Company).
 */
@Getter
@RequiredArgsConstructor
public enum CompanyErrorCode implements ErrorCode {

    // 400 - 요청 바디가 가리키는 대상이 없음 (업체 자체가 없는 404와 구분)
    COMPANY_HUB_NOT_FOUND(HttpStatus.BAD_REQUEST, "관리 허브로 지정할 허브를 찾을 수 없습니다."),
    COMPANY_MANAGER_NOT_FOUND(HttpStatus.BAD_REQUEST, "업체 관리자로 지정할 사용자를 찾을 수 없습니다."),

    // 404
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "업체를 찾을 수 없습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String message;
}
