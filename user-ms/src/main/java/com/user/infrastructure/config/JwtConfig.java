package com.user.infrastructure.config;

import com.user.infrastructure.jwt.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    /** 토큰 발급 시각(iat·exp)에 쓰는 시계. 테스트에서는 고정 Clock을 넣는다 */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
