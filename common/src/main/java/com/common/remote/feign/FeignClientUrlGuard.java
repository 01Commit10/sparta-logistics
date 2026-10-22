package com.common.remote.feign;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Feign은 우리 서비스끼리 Eureka 이름(name)으로만 호출한다 (C01 D2·C02 전제). 고정 url이 있는 클라이언트가 있으면 기동을 멈춘다.
 * <ul>
 *     <li>공통 ErrorDecoder는 상대가 공통 ErrorResponse를 준다고 보고, 공통 인터셉터는 사용자 헤더를 붙인다. 외부 API에 쓰면 오분류·헤더 유출</li>
 *     <li>검사: {@code @FeignClient(url)}(플레이스홀더를 푼 값)과 {@code spring.cloud.openfeign.client.config.*.url}</li>
 *     <li>메시지에 url 값은 넣지 않는다. Slack Webhook처럼 url 자체가 비밀값일 수 있다</li>
 *     <li>테스트(WireMock 고정 주소 등)만 {@code common.feign.allow-direct-url=true}로 허용한다</li>
 * </ul>
 */
public class FeignClientUrlGuard implements SmartInitializingSingleton {

    static final String ALLOW_DIRECT_URL = "common.feign.allow-direct-url";
    private static final String CLIENT_CONFIG = "spring.cloud.openfeign.client.config";
    /** lazy-attributes-resolution 모드에서 FeignClientsRegistrar가 남기는 속성 */
    private static final String LAZY_FACTORY_BEAN_ATTRIBUTE = "feignClientsRegistrarFactoryBean";

    private final ConfigurableListableBeanFactory beanFactory;
    private final Environment environment;

    public FeignClientUrlGuard(ConfigurableListableBeanFactory beanFactory, Environment environment) {
        this.beanFactory = beanFactory;
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (environment.getProperty(ALLOW_DIRECT_URL, Boolean.class, false)) {
            return;
        }
        List<String> violations = new ArrayList<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> clientType = feignClientType(beanFactory.getBeanDefinition(beanName));
            if (clientType == null) {
                continue;
            }
            FeignClient annotation = AnnotatedElementUtils.findMergedAnnotation(clientType, FeignClient.class);
            if (annotation != null && StringUtils.hasText(environment.resolvePlaceholders(annotation.url()))) {
                violations.add("@FeignClient(url) '" + clientName(annotation) + "' (" + clientType.getName() + ")");
            }
        }
        configuredUrls().forEach(client -> violations.add(CLIENT_CONFIG + "." + client + ".url"));
        if (!violations.isEmpty()) {
            throw new IllegalStateException("""
                    Feign 클라이언트에 고정 url이 있습니다: %s
                    Feign은 우리 서비스끼리 Eureka 이름(@FeignClient(name = "hub-ms"))으로만 호출합니다.
                    외부 API(Slack 등)는 RestClient를 쓰세요 (공통 ErrorDecoder·사용자 헤더 전달이 외부 응답·외부 서버에 적용되지 않게).
                    테스트에서만 %s=true로 허용할 수 있습니다.""".formatted(String.join(", ", violations), ALLOW_DIRECT_URL));
        }
    }

    /** eager 모드: FeignClientFactoryBean 정의(OBJECT_TYPE 속성), lazy 모드: 클라이언트 타입 정의(registrar 속성) */
    private Class<?> feignClientType(BeanDefinition definition) {
        Object objectType = definition.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
        if (objectType instanceof Class<?> type && isFeignClientFactory(definition)) {
            return type;
        }
        if (definition.getAttribute(LAZY_FACTORY_BEAN_ATTRIBUTE) != null && definition.getBeanClassName() != null) {
            return ClassUtils.resolveClassName(definition.getBeanClassName(), beanFactory.getBeanClassLoader());
        }
        return null;
    }

    private static boolean isFeignClientFactory(BeanDefinition definition) {
        return "org.springframework.cloud.openfeign.FeignClientFactoryBean".equals(definition.getBeanClassName());
    }

    private static String clientName(FeignClient annotation) {
        return StringUtils.hasText(annotation.name()) ? annotation.name() : annotation.value();
    }

    private List<String> configuredUrls() {
        Map<String, Map<String, Object>> config = Binder.get(environment)
                .bind(CLIENT_CONFIG, Bindable.mapOf(String.class, Object.class))
                .map(FeignClientUrlGuard::asClientMap)
                .orElse(Map.of());
        return config.entrySet().stream()
                .filter(entry -> entry.getValue().get("url") instanceof String url && StringUtils.hasText(url))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> asClientMap(Map<String, Object> raw) {
        Map<String, Map<String, Object>> clients = new java.util.LinkedHashMap<>();
        raw.forEach((client, value) -> {
            if (value instanceof Map<?, ?> map) {
                clients.put(client, (Map<String, Object>) map);
            }
        });
        return clients;
    }
}
