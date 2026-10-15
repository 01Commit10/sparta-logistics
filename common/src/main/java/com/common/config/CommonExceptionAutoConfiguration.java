package com.common.config;

import com.common.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 각 서비스의 @SpringBootApplication은 자기 패키지(com.order 등)만 스캔하므로
 * common 모듈의 빈은 auto-configuration으로 등록한다.
 * - 실행 시: META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * - @WebMvcTest 슬라이스 테스트 시: META-INF/spring/org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc.imports
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonExceptionAutoConfiguration {

    // 서비스에서 상속·교체가 필요하면 같은 타입의 빈을 등록해 덮어쓰기 가능.
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
