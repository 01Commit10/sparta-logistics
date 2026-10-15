package com.user.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 4계층 의존 규칙 (docs/plan/01 구현 원칙 2장).
 * presentation → application → domain ← infrastructure
 * domain 예외 허용: JPA 어노테이션(jakarta.persistence), common 예외(공유 커널)
 */
@AnalyzeClasses(packages = "com.user", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String PRESENTATION = "com.user.presentation..";
    private static final String APPLICATION = "com.user.application..";
    private static final String DOMAIN = "com.user.domain..";
    private static final String INFRASTRUCTURE = "com.user.infrastructure..";

    @ArchTest
    static final ArchRule domain은_기술과_다른_계층에_의존하지_않는다 =
            noClasses().that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "io.jsonwebtoken..", "io.lettuce..", "feign..",
                            APPLICATION, PRESENTATION, INFRASTRUCTURE);

    @ArchTest
    static final ArchRule application은_presentation과_infrastructure에_의존하지_않는다 =
            noClasses().that().resideInAPackage(APPLICATION)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            PRESENTATION, INFRASTRUCTURE, "io.jsonwebtoken..", "io.lettuce..",
                            "org.springframework.data.redis..", "org.springframework.data.jpa..",
                            "org.springframework.security..");

    @ArchTest
    static final ArchRule presentation은_domain과_infrastructure에_의존하지_않는다 =
            noClasses().that().resideInAPackage(PRESENTATION)
                    .should().dependOnClassesThat().resideInAnyPackage(DOMAIN, INFRASTRUCTURE);

    @ArchTest
    static final ArchRule infrastructure는_presentation과_application에_의존하지_않는다 =
            noClasses().that().resideInAPackage(INFRASTRUCTURE)
                    .should().dependOnClassesThat().resideInAnyPackage(PRESENTATION, APPLICATION);

    @ArchTest
    static final ArchRule facade만_다른_service를_묶는다 =
            noClasses().that().resideInAPackage("com.user.application..")
                    .and().resideOutsideOfPackage("com.user.application.facade..")
                    .should().dependOnClassesThat().resideInAPackage("com.user.application.facade..");

    @ArchTest
    static final ArchRule domain_Port의_구현체는_infrastructure에만_있다 =
            classes().that().implement(resideInAPackage(DOMAIN))
                    .and().resideOutsideOfPackage(DOMAIN)
                    .should().resideInAPackage(INFRASTRUCTURE);

    // 팀 공통 패키지 구조 (presentation/controller·dto, application/service·dto, domain/entity·vo·repository)
    @ArchTest
    static final ArchRule Controller는_presentation_controller에_있다 =
            classes().that().areAnnotatedWith(RestController.class)
                    .should().resideInAPackage("com.user.presentation.controller..");

    @ArchTest
    static final ArchRule Request_Response는_presentation_dto에_있다 =
            classes().that().haveSimpleNameEndingWith("Request").or().haveSimpleNameEndingWith("Response")
                    .should().resideInAnyPackage("com.user.presentation.dto.request..", "com.user.presentation.dto.response..");

    @ArchTest
    static final ArchRule Service는_application_service나_facade에_있다 =
            classes().that().areAnnotatedWith(Service.class)
                    .should().resideInAnyPackage("com.user.application.service..", "com.user.application.facade..");

    @ArchTest
    static final ArchRule Command_Result는_application_dto에_있다 =
            classes().that().haveSimpleNameEndingWith("Command").or().haveSimpleNameEndingWith("Result")
                    .should().resideInAPackage("com.user.application.dto..");

    @ArchTest
    static final ArchRule 엔티티는_domain_entity에_있다 =
            classes().that().areAnnotatedWith(Entity.class).or().areAnnotatedWith(MappedSuperclass.class)
                    .should().resideInAPackage("com.user.domain.entity..");

    @ArchTest
    static final ArchRule 저장소_Port는_domain_repository에_있다 =
            classes().that().resideInAPackage(DOMAIN).and().haveSimpleNameEndingWith("Repository")
                    .should().resideInAPackage("com.user.domain.repository..").andShould().beInterfaces();

    @ArchTest
    static final ArchRule 다른_서비스의_코드를_import하지_않는다 =
            noClasses().that().resideInAPackage("com.user..")
                    .should().dependOnClassesThat(resideInAnyPackage(
                            "com.gateway..", "com.hub..", "com.company..", "com.order..", "com.delivery..", "com.eureka.."));
}
