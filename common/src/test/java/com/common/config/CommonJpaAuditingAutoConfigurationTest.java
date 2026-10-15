package com.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.common.audit.UserIdAuditorAware;
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
