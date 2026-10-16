package com.common.config;

import com.common.auth.AuthContract;
import com.common.auth.AuthRole;
import com.common.auth.AuthUser;
import com.common.auth.CurrentUserProvider;
import com.common.auth.feign.UserHeaderRelayInterceptor;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommonAuthFeignAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonAuthFeignAutoConfiguration.class));

    @Test
    @DisplayName("16. Feign이 있으면 헤더 전달 인터셉터 하나만 등록한다")
    void registersRelayInterceptor() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RequestInterceptor.class);
            assertThat(context).hasSingleBean(UserHeaderRelayInterceptor.class);
        });
    }

    @Test
    @DisplayName("16. Feign이 없으면 등록하지 않는다")
    void skippedWithoutFeign() {
        runner.withClassLoader(new FilteredClassLoader(RequestInterceptor.class))
                .run(context -> assertThat(context).doesNotHaveBean(UserHeaderRelayInterceptor.class));
    }

    @Test
    @DisplayName("16. 서블릿이 없으면(WebFlux 등) 등록하지 않는다")
    void skippedWithoutServlet() {
        runner.withClassLoader(new FilteredClassLoader(HttpServletRequest.class))
                .run(context -> assertThat(context).doesNotHaveBean(UserHeaderRelayInterceptor.class));
    }

    @Test
    @DisplayName("16. CurrentUserProvider 빈이 있으면 그것으로 사용자를 꺼낸다")
    void usesProviderBean() {
        runner.withUserConfiguration(FixedProviderConfig.class).run(context -> {
            RequestTemplate template = new RequestTemplate();
            context.getBean(UserHeaderRelayInterceptor.class).apply(template);

            assertThat(template.headers().get(AuthContract.HEADER_USER_ID))
                    .containsExactly(FixedProviderConfig.USER_ID.toString());
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class FixedProviderConfig {
        static final UUID USER_ID = UUID.randomUUID();

        @Bean
        CurrentUserProvider fixedProvider() {
            return new CurrentUserProvider() {
                @Override
                public Optional<AuthUser> current() {
                    return Optional.of(new AuthUser(USER_ID, AuthRole.USER));
                }

                @Override
                public boolean inRequest() {
                    return true;
                }
            };
        }
    }
}
