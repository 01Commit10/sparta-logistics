package com.gateway.presentation.filter;

import org.springframework.core.Ordered;

/**
 * Gateway 전역 필터 실행 순서 (docs/plan/gateway 2.2). 숫자가 작을수록 먼저 실행된다.
 * 모두 라우팅(ReactiveLoadBalancerClientFilter, 10150)보다 앞이다.
 */
public final class FilterOrder {

    /** GW-08: X-Request-Id 부여·접근 로그 */
    public static final int REQUEST_ID = Ordered.HIGHEST_PRECEDENCE;
    /** GW-03: 외부에서 온 X-User-*, X-Token-* 제거 (공개 경로 포함 모든 요청) */
    public static final int HEADER_SANITIZE = Ordered.HIGHEST_PRECEDENCE + 1;
    /** GW-03: /internal/** 차단. GW-05에서 인증 필터의 첫 단계로 합친다 */
    public static final int BLOCKED_PATH = Ordered.HIGHEST_PRECEDENCE + 2;

    private FilterOrder() {
    }
}
