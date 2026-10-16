package com.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    // 400
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INVALID_SORT_PROPERTY(HttpStatus.BAD_REQUEST, "정렬할 수 없는 필드입니다."),

    // 401 - Gateway를 거치지 않아 X-User-* 헤더가 없는 경우 등
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),

    // 403
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // 404
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),

    // 405
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),

    // 500
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // 503, 504 - 서비스 간 호출 실패 (SYS-13 장애 대응 설계)
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 서비스를 이용할 수 없습니다."),
    SERVICE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "서비스 응답 시간이 초과되었습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String message;
}
