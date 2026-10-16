package com.common.remote.integration;

import com.common.config.CommonJpaAuditingAutoConfiguration;
import com.common.config.CommonRedisAutoConfiguration;
import feign.RequestInterceptor;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Feign 장애 대응 통합 테스트용 앱. 실제 서비스처럼 application-common.yaml(common 프로필)을 읽고,
 * Eureka 대신 SimpleDiscoveryClient로 hub-ms·company-ms 주소를 준다. DB·Redis는 쓰지 않는다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class,
        RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class, CacheAutoConfiguration.class,
        CommonRedisAutoConfiguration.class, CommonJpaAuditingAutoConfiguration.class})
@EnableFeignClients(clients = {HubTestClient.class, CompanyTestClient.class, OrderTestClient.class})
@Import(RemoteTestController.class)
class RemoteTestApplication {

    /** Feign 요청을 실행한 스레드 (C02 E2: 호출한 스레드에서 그대로 실행되는지) */
    static final AtomicReference<Thread> FEIGN_THREAD = new AtomicReference<>();

    @Bean
    RequestInterceptor threadRecorder() {
        return template -> FEIGN_THREAD.set(Thread.currentThread());
    }
}
