package com.gateway.domain.policy;

import java.util.List;

/**
 * Gateway 경로 정책 (docs/plan/gateway GW-03·05·06). 규칙은 application.yaml gateway.policy에서 읽는다.
 * <ul>
 *     <li>blocked: 외부에서 도달하면 안 되는 경로 (메서드 무관)</li>
 *     <li>public: 토큰 없이 통과하는 메서드 + 경로</li>
 *     <li>master: MASTER만 호출할 수 있는 메서드 + 경로. master-exclude(본인 경로 /users/me)가 먼저 적용된다</li>
 *     <li>password-change-allowed: 비밀번호 변경이 필요한 사용자(pcr=true)가 호출할 수 있는 요청 (GW-07)</li>
 * </ul>
 * 데이터를 봐야 아는 권한(업체관리자, 허브담당 등)은 각 서비스가 판단한다.
 */
public final class RoutePolicy {

    private final List<PathRule> blocked;
    private final List<EndpointRule> publicRules;
    private final List<EndpointRule> masterExcludeRules;
    private final List<EndpointRule> masterRules;
    private final List<EndpointRule> passwordChangeAllowedRules;

    private RoutePolicy(Builder builder) {
        this.blocked = builder.blocked.stream().map(PathRule::of).toList();
        this.publicRules = parse(builder.publicRules);
        this.masterExcludeRules = parse(builder.masterExcludeRules);
        this.masterRules = parse(builder.masterRules);
        this.passwordChangeAllowedRules = parse(builder.passwordChangeAllowedRules);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 외부에서 도달하면 안 되는 경로인지 (내부 API) */
    public boolean isBlocked(String path) {
        return blocked.stream().anyMatch(rule -> rule.matches(path));
    }

    /** 토큰 없이 통과하는 요청인지 */
    public boolean isPublic(String method, String path) {
        return anyMatch(publicRules, method, path);
    }

    /** MASTER만 호출할 수 있는 요청인지. 본인 경로(master-exclude)는 MASTER 규칙보다 먼저 제외한다 */
    public boolean requiresMaster(String method, String path) {
        if (anyMatch(masterExcludeRules, method, path)) {
            return false;
        }
        return anyMatch(masterRules, method, path);
    }

    /** 관리자가 비밀번호를 초기화한 사용자(pcr=true)도 호출할 수 있는 요청인지 (내 정보 조회, 비밀번호 변경, 로그아웃) */
    public boolean isAllowedDuringPasswordChange(String method, String path) {
        return anyMatch(passwordChangeAllowedRules, method, path);
    }

    private static boolean anyMatch(List<EndpointRule> rules, String method, String path) {
        return rules.stream().anyMatch(rule -> rule.matches(method, path));
    }

    private static List<EndpointRule> parse(List<String> specs) {
        return specs.stream().map(EndpointRule::parse).toList();
    }

    public static final class Builder {

        private List<String> blocked = List.of();
        private List<String> publicRules = List.of();
        private List<String> masterExcludeRules = List.of();
        private List<String> masterRules = List.of();
        private List<String> passwordChangeAllowedRules = List.of();

        private Builder() {
        }

        public Builder blocked(List<String> patterns) {
            this.blocked = List.copyOf(patterns);
            return this;
        }

        public Builder publicRules(List<String> specs) {
            this.publicRules = List.copyOf(specs);
            return this;
        }

        public Builder masterExcludeRules(List<String> specs) {
            this.masterExcludeRules = List.copyOf(specs);
            return this;
        }

        public Builder masterRules(List<String> specs) {
            this.masterRules = List.copyOf(specs);
            return this;
        }

        public Builder passwordChangeAllowedRules(List<String> specs) {
            this.passwordChangeAllowedRules = List.copyOf(specs);
            return this;
        }

        public RoutePolicy build() {
            return new RoutePolicy(this);
        }
    }
}
