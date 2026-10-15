package com.gateway.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * 하위 서비스 대신 WireMock 서버를 띄우고, SimpleDiscoveryClient에 등록한다.
 * Eureka 없이 lb://{서비스} 라우트를 그대로 테스트하기 위함 (docs/plan/01 구현 원칙 3.2).
 */
public final class WireMockServices {

    public static final List<String> SERVICES = List.of("user-ms", "company-ms", "hub-ms", "order-ms", "delivery-ms");

    private final Map<String, WireMockServer> servers = new LinkedHashMap<>();

    private WireMockServices(List<String> services) {
        services.forEach(name -> {
            WireMockServer server = new WireMockServer(options().dynamicPort());
            server.start();
            servers.put(name, server);
        });
    }

    public static WireMockServices start(List<String> services) {
        return new WireMockServices(services);
    }

    public static WireMockServices startAll() {
        return start(SERVICES);
    }

    public WireMockServer get(String service) {
        return servers.get(service);
    }

    public void registerInstances(DynamicPropertyRegistry registry) {
        servers.forEach((name, server) -> registry.add(
                "spring.cloud.discovery.client.simple.instances." + name + "[0].uri",
                () -> "http://localhost:" + server.port()));
    }

    public void resetAll() {
        servers.values().forEach(WireMockServer::resetAll);
    }

    public void stopAll() {
        servers.values().forEach(WireMockServer::stop);
    }
}
