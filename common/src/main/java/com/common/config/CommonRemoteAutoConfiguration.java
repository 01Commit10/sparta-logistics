package com.common.config;

import com.common.remote.RemoteCallTemplate;
import com.common.remote.feign.CommonFeignErrorDecoder;
import com.common.remote.feign.FeignClientUrlGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 서비스 간 Feign 호출 장애 대응 (C02). 헤더 전달은 별도(C01 CommonAuthFeignAutoConfiguration).
 * OpenFeign을 의존성에 추가한 서비스에서만 켜진다. 타임아웃·Circuit Breaker 값은 application-common.yaml에 있다.
 * ErrorDecoder 빈은 모든 Feign 클라이언트에 적용된다.
 */
@AutoConfiguration
@ConditionalOnClass(name = "feign.Feign")
public class CommonRemoteAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    public CommonFeignErrorDecoder commonFeignErrorDecoder(ObjectProvider<ObjectMapper> objectMapper) {
        return new CommonFeignErrorDecoder(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean
    public RemoteCallTemplate remoteCallTemplate() {
        return new RemoteCallTemplate();
    }

    @Bean
    @ConditionalOnMissingBean
    public FeignClientUrlGuard feignClientUrlGuard(ConfigurableListableBeanFactory beanFactory, Environment environment) {
        return new FeignClientUrlGuard(beanFactory, environment);
    }
}
