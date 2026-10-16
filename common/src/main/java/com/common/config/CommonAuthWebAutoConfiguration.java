package com.common.config;

import com.common.auth.CurrentUserProvider;
import com.common.auth.web.CurrentUserArgumentResolver;
import com.common.auth.web.CurrentUserHeaderInterceptor;
import com.common.auth.web.RequestHeaderCurrentUserProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * X-User-* 헤더 사용 설정 (C01). 서블릿(MVC) 서비스에서만 켜지고 Gateway(WebFlux)에서는 켜지지 않는다.
 * <ul>
 *     <li>{@code @CurrentUser AuthUser} 파라미터 리졸버</li>
 *     <li>요청 입구 검사: 잘못된 헤더 → 401 (모든 API)</li>
 *     <li>{@link CurrentUserProvider}: Controller 밖(AuditorAware 등)에서 현재 사용자 조회</li>
 * </ul>
 * - 실행 시: META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * - @WebMvcTest 슬라이스 테스트 시: META-INF/spring/org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc.imports
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
public class CommonAuthWebAutoConfiguration {

    // 비동기 전파 등 방식을 바꾸려면 서비스에서 같은 타입의 빈을 등록해 덮어쓰기 가능.
    @Bean
    @ConditionalOnMissingBean(CurrentUserProvider.class)
    public CurrentUserProvider currentUserProvider() {
        return new RequestHeaderCurrentUserProvider();
    }

    @Bean
    public WebMvcConfigurer commonAuthWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                // 서비스의 다른 인터셉터가 잘못된 헤더를 보기 전에 먼저 막는다
                registry.addInterceptor(new CurrentUserHeaderInterceptor()).order(Ordered.HIGHEST_PRECEDENCE);
            }

            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentUserArgumentResolver());
            }
        };
    }
}
