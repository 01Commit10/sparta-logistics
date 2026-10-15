package com.gateway.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import reactor.core.publisher.Mono;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 4계층 의존 규칙 (docs/plan/01 구현 원칙 2장).
 * presentation → application → domain ← infrastructure
 */
@AnalyzeClasses(packages = "com.gateway", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String PRESENTATION = "com.gateway.presentation..";
    private static final String APPLICATION = "com.gateway.application..";
    private static final String DOMAIN = "com.gateway.domain..";
    private static final String INFRASTRUCTURE = "com.gateway.infrastructure..";

    @ArchTest
    static final ArchRule domain은_기술과_다른_계층에_의존하지_않는다 =
            noClasses().that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "io.jsonwebtoken..", "io.lettuce..",
                            APPLICATION, PRESENTATION, INFRASTRUCTURE);

    @ArchTest
    static final ArchRule domain에서_Reactor는_Mono만_허용한다 =
            noClasses().that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat(resideInAPackage("reactor..").and(not(equivalentTo(Mono.class))));

    @ArchTest
    static final ArchRule application은_presentation과_infrastructure에_의존하지_않는다 =
            noClasses().that().resideInAPackage(APPLICATION)
                    .should().dependOnClassesThat().resideInAnyPackage(PRESENTATION, INFRASTRUCTURE);

    @ArchTest
    static final ArchRule presentation은_domain과_infrastructure에_의존하지_않는다 =
            noClasses().that().resideInAPackage(PRESENTATION)
                    .should().dependOnClassesThat().resideInAnyPackage(DOMAIN, INFRASTRUCTURE);

    @ArchTest
    static final ArchRule infrastructure는_presentation과_application에_의존하지_않는다 =
            noClasses().that().resideInAPackage(INFRASTRUCTURE)
                    .should().dependOnClassesThat().resideInAnyPackage(PRESENTATION, APPLICATION);

    @ArchTest
    static final ArchRule domain_Port의_구현체는_infrastructure에만_있다 =
            classes().that().implement(resideInAPackage(DOMAIN))
                    .and().resideOutsideOfPackage(DOMAIN)
                    .should().resideInAPackage(INFRASTRUCTURE);

    @ArchTest
    static final ArchRule 다른_서비스의_코드를_import하지_않는다 =
            noClasses().that().resideInAPackage("com.gateway..")
                    .should().dependOnClassesThat(resideInAnyPackage(
                            "com.user..", "com.hub..", "com.company..", "com.order..", "com.delivery..", "com.eureka.."));
}
