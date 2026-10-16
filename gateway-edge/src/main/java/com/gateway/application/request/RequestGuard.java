package com.gateway.application.request;

import com.gateway.domain.policy.RoutePolicy;
import com.gateway.domain.policy.TrustedHeaders;
import org.springframework.stereotype.Service;

/**
 * 인증과 무관하게 모든 요청에 적용하는 정리 규칙 (GW-03).
 * presentation 필터는 이 판단 결과만 적용한다.
 */
@Service
public class RequestGuard {

    private final RoutePolicy routePolicy;

    public RequestGuard(RoutePolicy routePolicy) {
        this.routePolicy = routePolicy;
    }

    /** 외부에서 도달하면 안 되는 경로(내부 API)인지 */
    public boolean isBlocked(String path) {
        return routePolicy.isBlocked(path);
    }

    /** 외부 요청에서 지워야 하는 신뢰 헤더인지 (X-User-*, X-Token-*) */
    public boolean isTrustedHeader(String headerName) {
        return TrustedHeaders.isTrusted(headerName);
    }
}
