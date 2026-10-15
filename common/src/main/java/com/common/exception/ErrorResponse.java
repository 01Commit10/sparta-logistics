package com.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 공통 에러 응답 형식. Gateway와 모든 서비스가 동일하게 사용한다.
 * <pre>
 * { "code": "INVALID_INPUT", "message": "잘못된 요청입니다.", "errors": [ { "field": "quantity", "reason": "1 이상이어야 합니다" } ] }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> errors
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), errors);
    }

    public record FieldError(String field, String reason) {
    }
}
