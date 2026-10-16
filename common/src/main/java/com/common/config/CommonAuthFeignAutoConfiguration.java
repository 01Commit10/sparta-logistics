package com.common.config;

import com.common.auth.CurrentUserProvider;
import com.common.auth.feign.UserHeaderRelayInterceptor;
import com.common.auth.web.RequestHeaderCurrentUserProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 서비스 간 Feign 호출에 사용자 헤더를 이어 보내는 설정 (C01). Feign 장애 대응은 별도(C02 CommonRemoteAutoConfiguration).
 * OpenFeign을 의존성에 추가한 서블릿 서비스에서만 켜진다. RequestInterceptor 빈은 모든 Feign 클라이언트에 적용된다.
 */
@AutoConfiguration(after = CommonAuthWebAutoConfiguration.class)
@ConditionalOnClass(name = {
        "feign.RequestInterceptor",
        "jakarta.servlet.http.HttpServletRequest",
        "org.springframework.web.context.request.RequestContextHolder"})
public class CommonAuthFeignAutoConfiguration {

    // CurrentUserProvider 빈이 없으면(웹 자동 설정이 없는 테스트 등) 기본 구현을 쓴다.
    @Bean
    @ConditionalOnMissingBean
    public UserHeaderRelayInterceptor userHeaderRelayInterceptor(ObjectProvider<CurrentUserProvider> currentUserProvider) {
        return new UserHeaderRelayInterceptor(currentUserProvider.getIfAvailable(RequestHeaderCurrentUserProvider::new));
    }
}
