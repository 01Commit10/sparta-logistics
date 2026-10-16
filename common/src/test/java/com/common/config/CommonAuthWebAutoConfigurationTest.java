package com.common.config;

import com.common.auth.AuthUser;
import com.common.auth.CurrentUserProvider;
import com.common.auth.web.CurrentUserArgumentResolver;
import com.common.auth.web.CurrentUserHeaderInterceptor;
import com.common.auth.web.RequestHeaderCurrentUserProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CommonAuthWebAutoConfigurationTest {

    final AutoConfigurations config = AutoConfigurations.of(CommonAuthWebAutoConfiguration.class);

    @Test
    @DisplayName("14. 서블릿 환경: Provider 빈, 리졸버·입구 인터셉터가 MVC에 등록된다")
    void servlet() {
        new WebApplicationContextRunner().withConfiguration(config).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).getBean(CurrentUserProvider.class).isInstanceOf(RequestHeaderCurrentUserProvider.class);
            assertThat(resolvers(context)).hasAtLeastOneElementOfType(CurrentUserArgumentResolver.class);
            assertThat(interceptors(context)).hasAtLeastOneElementOfType(CurrentUserHeaderInterceptor.class);
        });
    }

    @Test
    @DisplayName("14. 서비스가 CurrentUserProvider를 직접 등록하면 그것을 쓴다")
    void customProvider() {
        new WebApplicationContextRunner().withConfiguration(config).withUserConfiguration(CustomProvider.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CurrentUserProvider.class);
                    assertThat(context).getBean(CurrentUserProvider.class).isSameAs(context.getBean("customProvider"));
                });
    }

    @Test
    @DisplayName("15. WebFlux(Gateway)에서는 아무것도 등록하지 않는다")
    void reactive() {
        new ReactiveWebApplicationContextRunner().withConfiguration(config).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CurrentUserProvider.class);
            assertThat(context).doesNotHaveBean(WebMvcConfigurer.class);
        });
    }

    @Test
    @DisplayName("15. 웹이 아닌 환경에서도 등록하지 않는다")
    void nonWeb() {
        new ApplicationContextRunner().withConfiguration(config).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CurrentUserProvider.class);
        });
    }

    static List<HandlerMethodArgumentResolver> resolvers(ApplicationContext context) {
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
        context.getBeansOfType(WebMvcConfigurer.class).values().forEach(c -> c.addArgumentResolvers(resolvers));
        return resolvers;
    }

    static List<Object> interceptors(ApplicationContext context) {
        ProbeRegistry registry = new ProbeRegistry();
        context.getBeansOfType(WebMvcConfigurer.class).values().forEach(c -> c.addInterceptors(registry));
        return registry.all();
    }

    static class ProbeRegistry extends InterceptorRegistry {
        List<Object> all() {
            return getInterceptors();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomProvider {
        @Bean
        CurrentUserProvider customProvider() {
            return new CurrentUserProvider() {
                @Override
                public Optional<AuthUser> current() {
                    return Optional.empty();
                }

                @Override
                public boolean inRequest() {
                    return false;
                }
            };
        }
    }
}
