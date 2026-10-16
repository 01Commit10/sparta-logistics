package com.gateway.infrastructure.config;

import com.gateway.domain.policy.RoutePolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 application.yaml의 gateway.policy가 정책표(policy/master-cases.csv)대로 동작하는지 확인한다.
 * yaml만 고치고 정책표를 안 고치면(또는 반대) 이 테스트가 실패한다.
 */
class RoutePolicyPropertiesTest {

    private static RoutePolicy policy;

    @BeforeAll
    static void bindRealYaml() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yaml", new ClassPathResource("application.yaml"));
        sources.forEach(environment.getPropertySources()::addLast);
        RoutePolicyProperties properties = Binder.get(environment)
                .bind("gateway.policy", RoutePolicyProperties.class)
                .get();
        policy = new RoutePolicyConfig().routePolicy(properties);
    }

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvFileSource(resources = "/policy/master-cases.csv")
    @DisplayName("application.yaml의 MASTER 규칙 = 정책표")
    void masterRulesMatchPolicyTable(String method, String path, boolean expected) {
        assertThat(policy.requiresMaster(method, path)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /api/v1/auth/signup",
            "POST, /api/v1/auth/login",
            "POST, /api/v1/auth/reissue",
            "GET, /api/v1/auth/check-username"
    })
    @DisplayName("application.yaml의 공개 경로 (docs/design/04 API 명세서 Auth '비회원')")
    void publicRules(String method, String path) {
        assertThat(policy.isPublic(method, path)).isTrue();
    }

    @Test
    @DisplayName("로그아웃·라우팅 확인·내 정보는 공개가 아니다")
    void notPublic() {
        assertThat(policy.isPublic("POST", "/api/v1/auth/logout")).isFalse();
        assertThat(policy.isPublic("GET", "/api/v1/auth/routing-check")).isFalse();
        assertThat(policy.isPublic("GET", "/api/v1/users/me")).isFalse();
    }

    @Test
    @DisplayName("application.yaml의 비밀번호 변경 중 허용 경로 = GW-07 목록 3개")
    void passwordChangeAllowed() {
        assertThat(policy.isAllowedDuringPasswordChange("GET", "/api/v1/users/me")).isTrue();
        assertThat(policy.isAllowedDuringPasswordChange("PATCH", "/api/v1/users/me/password")).isTrue();
        assertThat(policy.isAllowedDuringPasswordChange("POST", "/api/v1/auth/logout")).isTrue();
        assertThat(policy.isAllowedDuringPasswordChange("PATCH", "/api/v1/users/me")).isFalse();
        assertThat(policy.isAllowedDuringPasswordChange("GET", "/api/v1/orders")).isFalse();
    }

    @Test
    @DisplayName("차단 경로는 GW-03 그대로 유지된다")
    void blocked() {
        assertThat(policy.isBlocked("/internal/v1/users/1")).isTrue();
        assertThat(policy.isBlocked("/api/v1/internal/x")).isTrue();
    }

    @Test
    @DisplayName("yaml 규칙 문법이 틀리면 정책을 만들 때 실패한다 (기동 실패)")
    void invalidRuleFailsFast() {
        RoutePolicyProperties properties = new RoutePolicyProperties(
                List.of(), List.of("/api/v1/auth/login"), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> new RoutePolicyConfig().routePolicy(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/api/v1/auth/login");
    }

    @Test
    @DisplayName("public 키는 Java 예약어라 publicRules로 바인딩한다")
    void bindsPublicKey() {
        RoutePolicyProperties properties = Binder.get(environmentWith("gateway.policy.public[0]=GET /a"))
                .bind("gateway.policy", RoutePolicyProperties.class)
                .get();

        assertThat(properties.publicRules()).containsExactly("GET /a");
    }

    private static StandardEnvironment environmentWith(String property) {
        StandardEnvironment environment = new StandardEnvironment();
        String[] keyValue = property.split("=", 2);
        environment.getPropertySources().addFirst(
                new MapPropertySource("test", Map.of(keyValue[0], keyValue[1])));
        ConfigurationPropertySources.attach(environment);
        return environment;
    }
}
