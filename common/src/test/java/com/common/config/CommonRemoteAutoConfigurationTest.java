package com.common.config;

import com.common.remote.RemoteCallTemplate;
import com.common.remote.feign.CommonFeignErrorDecoder;
import com.common.remote.feign.FeignClientUrlGuard;
import feign.Feign;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C02 테스트 12: Feign이 있는 서비스에만 장애 대응 빈을 등록한다.
 */
class CommonRemoteAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonRemoteAutoConfiguration.class));

    @Test
    @DisplayName("Feign이 있으면 ErrorDecoder·RemoteCallTemplate·URL 가드를 등록한다")
    void registersBeans() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).getBean(ErrorDecoder.class).isInstanceOf(CommonFeignErrorDecoder.class);
            assertThat(context).hasSingleBean(RemoteCallTemplate.class);
            assertThat(context).hasSingleBean(FeignClientUrlGuard.class);
        });
    }

    @Test
    @DisplayName("Feign이 없으면(Gateway 등) 아무것도 등록하지 않는다")
    void skippedWithoutFeign() {
        runner.withClassLoader(new FilteredClassLoader(Feign.class)).run(context -> {
            assertThat(context).doesNotHaveBean(RemoteCallTemplate.class);
            assertThat(context).doesNotHaveBean(FeignClientUrlGuard.class);
        });
    }

    @Test
    @DisplayName("서비스가 ErrorDecoder를 직접 만들면 공통 ErrorDecoder는 물러난다")
    void customErrorDecoderWins() {
        runner.withUserConfiguration(CustomDecoder.class).run(context ->
                assertThat(context).getBean(ErrorDecoder.class).isSameAs(CustomDecoder.DECODER));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDecoder {
        static final ErrorDecoder DECODER = new ErrorDecoder.Default();

        @Bean
        ErrorDecoder customErrorDecoder() {
            return DECODER;
        }
    }
}
