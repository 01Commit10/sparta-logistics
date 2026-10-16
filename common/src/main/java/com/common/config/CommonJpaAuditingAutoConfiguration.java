package com.common.config;

import com.common.audit.UserIdAuditorAware;
import com.common.auth.CurrentUserProvider;
import com.common.auth.web.RequestHeaderCurrentUserProvider;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.UUID;

/**
 * BaseEntity의 Audit 컬럼을 채우는 JPA Auditing 설정.
 * - 실행 시: META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * - @DataJpaTest 슬라이스 테스트 시: META-INF/spring/org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureDataJpa.imports
 *
 * 서비스에서 @EnableJpaAuditing을 직접 선언하면 jpaAuditingHandler 빈이 중복되므로,
 * 이미 선언된 경우엔 이 설정 전체를 건너뛴다(그때는 서비스가 AuditorAware도 직접 제공).
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({EntityManager.class, EnableJpaAuditing.class})
@ConditionalOnMissingBean(name = "jpaAuditingHandler")
@EnableJpaAuditing
public class CommonJpaAuditingAutoConfiguration {

    // 작업자는 HTTP 요청 헤더에서 읽으므로 servlet 환경에서만 등록.
    // 그 외 환경에선 AuditorAware 없이 생성·수정 시각만 기록.
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "jakarta.servlet.http.HttpServletRequest",
            "org.springframework.web.context.request.RequestContextHolder"})
    static class UserIdAuditorConfiguration {

        // 서비스에서 작업자 결정 방식을 바꾸려면 AuditorAware 빈을 등록해 덮어쓰기 가능.
        // CurrentUserProvider 빈이 없으면(@DataJpaTest 등 웹 자동 설정이 없는 경우) 기본 구현을 쓴다.
        @Bean
        @ConditionalOnMissingBean(AuditorAware.class)
        AuditorAware<UUID> userIdAuditorAware(ObjectProvider<CurrentUserProvider> currentUserProvider) {
            return new UserIdAuditorAware(currentUserProvider.getIfAvailable(RequestHeaderCurrentUserProvider::new));
        }
    }
}
