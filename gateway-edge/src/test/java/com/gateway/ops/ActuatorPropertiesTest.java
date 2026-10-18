package com.gateway.ops;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 application.yaml의 actuator 설정 (GW-11). 통합 테스트는 관리 포트를 0(무작위)으로 바꾸므로 운영 값은 여기서 확인한다.
 */
class ActuatorPropertiesTest {

    private static Binder binder() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application.yaml", new ClassPathResource("application.yaml"))
                .forEach(environment.getPropertySources()::addLast);
        return Binder.get(environment);
    }

    @Test
    @DisplayName("관리 포트는 서비스 포트(19090)와 다른 19190")
    void managementPort() throws IOException {
        Binder binder = binder();

        assertThat(binder.bind("management.server.port", Integer.class).get()).isEqualTo(19190);
        assertThat(binder.bind("server.port", Integer.class).get()).isEqualTo(19090);
    }

    @Test
    @DisplayName("관리 포트는 다른 모듈의 서버 포트와 겹치지 않는다 (로컬에서 bootRun을 함께 띄움)")
    void managementPortDoesNotCollideWithOtherModules() throws IOException {
        int managementPort = binder().bind("management.server.port", Integer.class).get();

        List<Integer> otherPorts = otherModuleServerPorts();

        assertThat(otherPorts).hasSizeGreaterThanOrEqualTo(6);   // eureka + 서비스 5개를 실제로 읽었는지
        assertThat(otherPorts).doesNotContain(managementPort);
    }

    /** 테스트 작업 디렉터리는 gateway-edge 모듈이므로 형제 모듈의 application.yaml을 읽는다 */
    private static List<Integer> otherModuleServerPorts() throws IOException {
        List<Integer> ports = new ArrayList<>();
        try (Stream<Path> modules = Files.list(Path.of("..").toAbsolutePath().normalize())) {
            for (Path yaml : modules.filter(module -> !module.endsWith("gateway-edge"))
                    .map(module -> module.resolve("src/main/resources/application.yaml"))
                    .filter(Files::exists).toList()) {
                StandardEnvironment environment = new StandardEnvironment();
                new YamlPropertySourceLoader().load(yaml.toString(), new FileSystemResource(yaml))
                        .forEach(environment.getPropertySources()::addLast);
                Binder.get(environment).bind("server.port", Integer.class).ifBound(ports::add);
            }
        }
        return ports;
    }

    @Test
    @DisplayName("liveness는 프로세스 상태만, readiness는 Redis를 빼고 판단한다")
    void probeGroups() throws IOException {
        Binder binder = binder();

        assertThat(binder.bind("management.endpoint.health.group.liveness.include", Bindable.listOf(String.class)).get())
                .containsExactly("livenessState");
        assertThat(binder.bind("management.endpoint.health.group.readiness.include", Bindable.listOf(String.class)).get())
                .containsExactly("readinessState");
    }

    @Test
    @DisplayName("노출 엔드포인트는 헬스·정보·라우트만")
    void exposure() throws IOException {
        assertThat(binder().bind("management.endpoints.web.exposure.include", Bindable.listOf(String.class)).get())
                .containsExactlyInAnyOrder("health", "info", "gateway");
    }
}
