package com.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모든 ErrorCode enum의 code 값은 전체에서 유일해야 한다 (ErrorCode 주석의 규칙).
 * 서비스 간 Feign 호출에서 원격 code 문자열을 ErrorCode로 복원하기 때문이다.
 * 새 ErrorCode enum을 추가하면 {@link ErrorCodeRegistry}에 등록한다 (빠뜨리면 allErrorCodeEnumsAreRegistered가 실패한다).
 * 등록 목록은 원격 code 복원(C02)과 이 검사가 함께 쓴다.
 */
class ErrorCodeUniquenessTest {

    static final List<ErrorCode> ALL_CODES = ErrorCodeRegistry.all();

    @Test
    @DisplayName("ErrorCodeRegistry는 com.common의 모든 ErrorCode enum을 포함한다 (등록 누락 방지)")
    void allErrorCodeEnumsAreRegistered() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(ErrorCode.class));
        // enum만 등록 대상이다. 원격에서 받은 모르는 code를 담는 RemoteErrorCode(record)는 제외
        List<String> found = scanner.findCandidateComponents("com.common").stream()
                .map(BeanDefinition::getBeanClassName)
                .filter(ErrorCodeUniquenessTest::isEnum)
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

    private static boolean isEnum(String className) {
        try {
            return Class.forName(className).isEnum();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
