package com.gateway.domain.policy;

import java.util.List;

/**
 * Gateway 경로 정책 (docs/plan/gateway 1.3 경로 정책표).
 * 지금은 차단 경로만 다룬다. 공개·MASTER 전용·비밀번호 변경 허용 경로는 GW-05·06·07에서 추가한다.
 */
public final class RoutePolicy {

    private final List<PathRule> blocked;

    private RoutePolicy(List<PathRule> blocked) {
        this.blocked = blocked;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 외부에서 도달하면 안 되는 경로인지 (내부 API) */
    public boolean isBlocked(String path) {
        return blocked.stream().anyMatch(rule -> rule.matches(path));
    }

    public static final class Builder {

        private List<String> blocked = List.of();

        private Builder() {
        }

        public Builder blocked(List<String> patterns) {
            this.blocked = List.copyOf(patterns);
            return this;
        }

        public RoutePolicy build() {
            return new RoutePolicy(blocked.stream().map(PathRule::of).toList());
        }
    }
}
