package com.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.common.audit.UserIdAuditorAware;
import com.common.auth.AuthContract;
import com.common.auth.AuthRole;
import com.common.auth.AuthUser;
import com.common.auth.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

class CommonJpaAuditingAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class,
                    CommonJpaAuditingAutoConfiguration.class))
            .withUserConfiguration(EntityPackage.class);

    @Test
    void registersAuditingWithUserIdAuditor() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("jpaAuditingHandler");
            assertThat(context).getBean(AuditorAware.class).isInstanceOf(UserIdAuditorAware.class);
        });
    }

    @Test
    void serviceAuditorAwareTakesPrecedence() {
        runner.withUserConfiguration(CustomAuditorConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AuditorAware.class);
            assertThat(context).getBean(AuditorAware.class).isSameAs(context.getBean("customAuditorAware"));
        });
    }

    @Test
    void backsOffWhenServiceDeclaresEnableJpaAuditing() {
        runner.withUserConfiguration(ServiceAuditingConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CommonJpaAuditingAutoConfiguration.class);
            assertThat(context).doesNotHaveBean(UserIdAuditorAware.class);
        });
    }

    @Test
    void registersAuditingWithoutAuditorOutsideServletEnvironment() {
        runner.withClassLoader(new FilteredClassLoader(HttpServletRequest.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("jpaAuditingHandler");
            assertThat(context).doesNotHaveBean(AuditorAware.class);
        });
    }

    @Test
    void auditorWorksWithoutCurrentUserProviderBean() {
        // @DataJpaTest처럼 웹 자동 설정이 없어 Provider 빈이 없어도 기본 Provider로 동작 (요청 밖 → 시스템)
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(CurrentUserProvider.class);
            assertThat(context.getBean(AuditorAware.class).getCurrentAuditor()).contains(AuthContract.SYSTEM_USER_ID);
        });
    }

    @Test
    void auditorUsesCurrentUserProviderBean() {
        runner.withUserConfiguration(FixedProviderConfig.class).run(context -> {
            assertThat(context.getBean(AuditorAware.class).getCurrentAuditor()).contains(FixedProviderConfig.USER_ID);
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

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage(basePackages = "com.common.entity")
    static class EntityPackage {
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomAuditorConfig {
        @Bean
        AuditorAware<UUID> customAuditorAware() {
            return Optional::empty;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableJpaAuditing
    static class ServiceAuditingConfig {
    }
}
