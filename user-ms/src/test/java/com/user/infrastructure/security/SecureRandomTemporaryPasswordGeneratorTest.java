package com.user.infrastructure.security;

import com.user.domain.service.PasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 임시 비밀번호 생성기 (U18 테스트 1).
 */
class SecureRandomTemporaryPasswordGeneratorTest {

    private final SecureRandomTemporaryPasswordGenerator generator = new SecureRandomTemporaryPasswordGenerator();

    @Test
    @DisplayName("1. 1,000회 생성 모두 12자이고 PasswordPolicy를 통과한다")
    void satisfiesPolicy() {
        IntStream.range(0, 1_000).mapToObj(i -> generator.generate()).forEach(password -> {
            assertThat(password).hasSize(12);
            assertThatCode(() -> PasswordPolicy.validate(password)).doesNotThrowAnyException();
        });
    }

    @Test
    @DisplayName("1. 구두로 전달하므로 헷갈리는 문자(0 O 1 l I o)는 쓰지 않는다")
    void noAmbiguousCharacters() {
        IntStream.range(0, 1_000).mapToObj(i -> generator.generate())
                .forEach(password -> assertThat(password).doesNotContainPattern("[0O1lIo]"));
    }

    @Test
    @DisplayName("1. 매번 다르고, 같은 위치에 같은 종류만 오지 않는다 (섞는다)")
    void randomAndShuffled() {
        Set<String> passwords = new HashSet<>();
        Set<Character> firstChars = new HashSet<>();
        IntStream.range(0, 1_000).mapToObj(i -> generator.generate()).forEach(password -> {
            passwords.add(password);
            firstChars.add(password.charAt(0));
        });

        assertThat(passwords).hasSize(1_000);
        assertThat(firstChars).anyMatch(Character::isUpperCase).anyMatch(Character::isLowerCase)
                .anyMatch(Character::isDigit).anyMatch(c -> !Character.isLetterOrDigit(c));
    }
}
