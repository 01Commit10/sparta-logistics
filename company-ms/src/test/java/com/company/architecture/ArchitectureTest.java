package com.company.architecture;

import com.common.page.PagePolicy;
import com.common.page.PageResponse;
import com.common.page.SpringDataPages;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 4계층 의존 규칙: presentation → application → domain ← infrastructure
 * domain 예외 허용: JPA 어노테이션(jakarta.persistence), common 예외·BaseEntity(공유 커널)
 */
@AnalyzeClasses(packages = "com.company", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String PRESENTATION = "com.company..presentation..";
    private static final String APPLICATION = "com.company..application..";
    private static final String DOMAIN = "com.company..domain..";
    private static final String INFRASTRUCTURE = "com.company..infrastructure..";

    @ArchTest
    static final ArchRule domain은_기술과_다른_계층에_의존하지_않는다 =
            noClasses().that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "feign..", "com.fasterxml..",
                            APPLICATION, PRESENTATION, INFRASTRUCTURE);

    @ArchTest
    static final ArchRule application은_presentation과_infrastructure에_의존하지_않는다 =
            noClasses().that().resideInAPackage(APPLICATION)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            PRESENTATION, INFRASTRUCTURE, "org.springframework.data.jpa..", "feign..",
                            "org.springframework.cloud.openfeign..");

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
    static final ArchRule Controller는_presentation_controller에_있다 =
            classes().that().areAnnotatedWith(RestController.class)
                    .should().resideInAPackage("com.company..presentation.controller..");

    @ArchTest
    static final ArchRule Request_Response는_presentation_dto에_있다 =
            classes().that().haveSimpleNameEndingWith("Request").or().haveSimpleNameEndingWith("Response")
                    .should().resideInAPackage("com.company..presentation.dto..");

    @ArchTest
    static final ArchRule Service는_application_service에_있다 =
            classes().that().areAnnotatedWith(Service.class)
                    .should().resideInAPackage("com.company..application.service..");

    @ArchTest
    static final ArchRule Command_Result는_application_dto에_있다 =
            classes().that().haveSimpleNameEndingWith("Command").or().haveSimpleNameEndingWith("Result")
                    .should().resideInAPackage("com.company..application.dto..");

    @ArchTest
    static final ArchRule 엔티티는_domain_entity에_있다 =
            classes().that().areAnnotatedWith(Entity.class)
                    .should().resideInAPackage("com.company..domain.entity..");

    @ArchTest
    static final ArchRule 저장소_Port는_domain_repository에_있다 =
            classes().that().resideInAPackage(DOMAIN).and().haveSimpleNameEndingWith("Repository")
                    .should().resideInAPackage("com.company..domain.repository..").andShould().beInterfaces();

    // PagePolicy·PageResponse·SpringDataPages는 Spring에 의존하지만 패키지가 com.common.page라 위 규칙으로 잡히지 않는다
    @ArchTest
    static final ArchRule 페이지_도구는_presentation에서만_쓴다 =
            noClasses().that().resideInAnyPackage(DOMAIN, APPLICATION, INFRASTRUCTURE)
                    .should().dependOnClassesThat().belongToAnyOf(PagePolicy.class, PageResponse.class);

    @ArchTest
    static final ArchRule SpringDataPages는_infrastructure에서만_쓴다 =
            noClasses().that().resideInAnyPackage(DOMAIN, APPLICATION, PRESENTATION)
                    .should().dependOnClassesThat().belongToAnyOf(SpringDataPages.class);

    @ArchTest
    static final ArchRule 다른_서비스의_코드를_import하지_않는다 =
            noClasses().that().resideInAPackage("com.company..")
                    .should().dependOnClassesThat(resideInAnyPackage(
                            "com.gateway..", "com.hub..", "com.user..", "com.order..", "com.delivery..", "com.eureka.."));
}
