package com.common.config;

import com.common.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * 각 서비스의 @SpringBootApplication은 자기 패키지(com.order 등)만 스캔하므로
 * common 모듈의 빈은 auto-configuration으로 등록한다.
 * (META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(GlobalExceptionHandler.class)
public class CommonExceptionAutoConfiguration {
}
