package com.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모든 ErrorCode enum의 code 값은 전체에서 유일해야 한다 (ErrorCode 주석의 규칙).
 * 서비스 간 Feign 호출에서 원격 code 문자열을 ErrorCode로 복원하기 때문이다.
 * 새 ErrorCode enum을 추가하면 ALL_CODES에도 추가한다 (빠뜨리면 allErrorCodeEnumsAreRegistered가 실패한다).
 */
class ErrorCodeUniquenessTest {

    static final List<ErrorCode> ALL_CODES = Stream.of(
                    CommonErrorCode.values(),
                    AuthErrorCode.values(),
                    UserErrorCode.values())
            .flatMap(Arrays::stream)
            .map(ErrorCode.class::cast)
            .toList();

    @Test
    @DisplayName("ALL_CODES는 com.common의 모든 ErrorCode enum을 포함한다 (등록 누락 방지)")
    void allErrorCodeEnumsAreRegistered() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(ErrorCode.class));
        List<String> found = scanner.findCandidateComponents("com.common").stream()
                .map(BeanDefinition::getBeanClassName)
                .toList();

        List<String> registered = ALL_CODES.stream()
                .map(code -> code.getClass().getName())
                .distinct()
                .toList();

        assertThat(registered).containsExactlyInAnyOrderElementsOf(found);
    }

    @Test
    @DisplayName("code 값은 모든 ErrorCode enum에서 유일하다")
    void codesAreUnique() {
        List<String> codes = ALL_CODES.stream().map(ErrorCode::getCode).toList();

        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("모든 코드는 HTTP 상태와 비어 있지 않은 메시지를 가진다")
    void everyCodeHasStatusAndMessage() {
        assertThat(ALL_CODES).allSatisfy(code -> {
            assertThat(code.getHttpStatus()).isNotNull();
            assertThat(code.getMessage()).isNotBlank();
        });
    }
}
