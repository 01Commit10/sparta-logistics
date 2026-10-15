package com.gateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * application.yaml의 gateway.policy 경로 목록.
 * 경로가 바뀌어도 코드를 고치지 않도록 정책은 설정에 둔다.
 */
@ConfigurationProperties(prefix = "gateway.policy")
public record RoutePolicyProperties(List<String> blocked) {

    public RoutePolicyProperties {
        blocked = blocked == null ? List.of() : List.copyOf(blocked);
    }
}
