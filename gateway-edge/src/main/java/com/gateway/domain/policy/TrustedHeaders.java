package com.gateway.domain.policy;

import java.util.List;
import java.util.Locale;

/**
 * Gateway만 넣을 수 있는 신뢰 헤더 (docs/plan/02 인증 계약 2.3).
 * 새 신뢰 헤더가 생겨도 빠지지 않도록 이름이 아니라 접두사로 판단한다.
 */
public final class TrustedHeaders {

    private static final List<String> PREFIXES = List.of("x-user-", "x-token-");

    private TrustedHeaders() {
    }

    public static boolean isTrusted(String headerName) {
        String lower = headerName.toLowerCase(Locale.ROOT);
        return PREFIXES.stream().anyMatch(lower::startsWith);
    }
}
