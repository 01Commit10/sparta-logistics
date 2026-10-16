package com.gateway.infrastructure.config;

import com.gateway.domain.policy.RoutePolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RoutePolicyProperties.class)
public class RoutePolicyConfig {

    @Bean
    public RoutePolicy routePolicy(RoutePolicyProperties properties) {
        return RoutePolicy.builder()
                .blocked(properties.blocked())
                .publicRules(properties.publicRules())
                .masterExcludeRules(properties.masterExclude())
                .masterRules(properties.master())
                .passwordChangeAllowedRules(properties.passwordChangeAllowed())
                .build();
    }
}
