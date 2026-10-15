package com.user.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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

    @ArchTest
    static final ArchRule 다른_서비스의_코드를_import하지_않는다 =
            noClasses().that().resideInAPackage("com.user..")
                    .should().dependOnClassesThat(resideInAnyPackage(
                            "com.gateway..", "com.hub..", "com.company..", "com.order..", "com.delivery..", "com.eureka.."));
}
