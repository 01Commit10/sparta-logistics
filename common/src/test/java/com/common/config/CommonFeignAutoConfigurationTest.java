package com.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.common.feign.UserIdRelayInterceptor;
import feign.RequestInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CommonFeignAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonFeignAutoConfiguration.class));

    @Test
    void registersUserIdRelayInterceptor() {
        runner.run(context -> assertThat(context).hasSingleBean(UserIdRelayInterceptor.class));
    }

    @Test
    void skippedWithoutFeign() {
        runner.withClassLoader(new FilteredClassLoader(RequestInterceptor.class))
                .run(context -> assertThat(context).doesNotHaveBean(UserIdRelayInterceptor.class));
    }
}
