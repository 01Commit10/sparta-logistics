package com.common.remote.integration;

import com.common.config.CommonJpaAuditingAutoConfiguration;
import com.common.config.CommonRedisAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

/**
 * Feign 원격 호출 통합 테스트용 앱. 실제 서비스처럼 application-common.yaml(common 프로필)을 읽고,
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
}
