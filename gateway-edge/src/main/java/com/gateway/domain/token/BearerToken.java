package com.gateway.domain.token;

import java.util.Optional;

/**
 * Authorization 헤더에서 Bearer 토큰 값을 꺼낸다.
 * 접두사는 대소문자를 구분하지 않고, 값이 비어 있으면 토큰이 없는 것으로 본다 (TOKEN_MISSING).
 */
public final class BearerToken {

    private static final String PREFIX = "Bearer ";

    private BearerToken() {
    }

    public static Optional<String> extract(String authorization) {
        if (authorization == null) {
            return Optional.empty();
        }
        String header = authorization.strip();
        if (!header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return Optional.empty();
        }
        String token = header.substring(PREFIX.length()).strip();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
