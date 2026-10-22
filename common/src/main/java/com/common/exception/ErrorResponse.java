package com.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 공통 에러 응답 형식. (SYS-01 6장: Gateway와 서비스가 같은 포맷을 사용)
 * Spring의 {@link org.springframework.web.ErrorResponse}와 이름이 같으므로 import 시 주의.
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

    public record FieldError(String field, String reason) {
    }
}
