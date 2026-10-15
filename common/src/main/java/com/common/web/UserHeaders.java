package com.common.web;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Gateway가 넣은 사용자 헤더 (docs/Common/SYS-01 5.1).
 * 현재 스레드에 묶인 HTTP 요청에서만 읽을 수 있다. (스케줄러·비동기 스레드에서는 empty)
 */
public final class UserHeaders {

    public static final String USER_ID = "X-User-Id";

    private UserHeaders() {
    }

    public static Optional<String> currentUserId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty();
        }

        String userId = servletAttributes.getRequest().getHeader(USER_ID);
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(userId.trim());
    }
}
