package com.gateway.application.auth.dto;

import com.common.exception.ErrorCode;

import java.util.Map;
import java.util.Objects;

/**
 * 인증·인가 결과. presentation 필터는 이 결과만 적용한다.
 * <ul>
 *     <li>통과: headers를 요청에 추가하고 라우팅 (공개 경로는 headers가 비어 있다)</li>
 *     <li>거부: errorCode로 응답하고 라우팅하지 않는다</li>
 * </ul>
 */
public record AuthResult(boolean allowed, Map<String, String> headers, ErrorCode errorCode) {

    public AuthResult {
        headers = Map.copyOf(headers);
    }

    public static AuthResult anonymous() {
        return new AuthResult(true, Map.of(), null);
    }

    public static AuthResult authenticated(Map<String, String> headers) {
        return new AuthResult(true, headers, null);
    }

    public static AuthResult denied(ErrorCode errorCode) {
        return new AuthResult(false, Map.of(), Objects.requireNonNull(errorCode));
    }
}
