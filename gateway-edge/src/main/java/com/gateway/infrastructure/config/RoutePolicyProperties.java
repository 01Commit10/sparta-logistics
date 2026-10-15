package com.gateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

import java.util.List;

/**
 * application.yaml의 gateway.policy 경로 목록.
 * 경로가 바뀌어도 코드를 고치지 않도록 정책은 설정에 둔다.
 * <ul>
 *     <li>blocked: 경로 패턴 ({@code /internal/**})</li>
 *     <li>public, master-exclude, master: {@code "메서드[,메서드] 경로"} ({@code "GET,PATCH /api/v1/users/*"}, 메서드 {@code *}는 전체)</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "gateway.policy")
public record RoutePolicyProperties(
        List<String> blocked,
        @Name("public") List<String> publicRules,
        List<String> masterExclude,
        List<String> master
) {

    public RoutePolicyProperties {
        blocked = copy(blocked);
        publicRules = copy(publicRules);
        masterExclude = copy(masterExclude);
        master = copy(master);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
