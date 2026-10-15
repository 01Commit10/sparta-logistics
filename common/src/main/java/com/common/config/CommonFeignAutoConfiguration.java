package com.common.config;

import com.common.feign.UserIdRelayInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 서비스 간 Feign 호출 공통 설정.
 * OpenFeign을 의존성에 추가한 서비스에서만 적용된다.
 * RequestInterceptor 빈은 모든 Feign 클라이언트에 자동으로 적용.
 */
@AutoConfiguration
@ConditionalOnClass(name = {
        "feign.RequestInterceptor",
        "jakarta.servlet.http.HttpServletRequest",
        "org.springframework.web.context.request.RequestContextHolder"})
public class CommonFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public UserIdRelayInterceptor userIdRelayInterceptor() {
        return new UserIdRelayInterceptor();
    }
}
