package com.user.infrastructure.security;

import com.user.domain.service.TemporaryPasswordGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 임시 비밀번호 12자: 대문자·소문자·숫자·특수문자를 하나씩 먼저 뽑고 나머지를 전체에서 채운 뒤 섞는다 (PasswordPolicy 항상 통과).
 * MASTER가 사용자에게 직접 불러 주므로 헷갈리는 문자(0 O 1 l I o)는 뺀다.
 */
@Component
public class SecureRandomTemporaryPasswordGenerator implements TemporaryPasswordGenerator {

    private static final int LENGTH = 12;
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGIT = "23456789";
    private static final String SPECIAL = "!@#$%^&*?";
    private static final String ALL = UPPER + LOWER + DIGIT + SPECIAL;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        char[] password = new char[LENGTH];
        password[0] = pick(UPPER);
        password[1] = pick(LOWER);
        password[2] = pick(DIGIT);
        password[3] = pick(SPECIAL);
        for (int i = 4; i < LENGTH; i++) {
            password[i] = pick(ALL);
        }
        for (int i = LENGTH - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = password[i];
            password[i] = password[j];
            password[j] = tmp;
        }
        return new String(password);
    }

    private char pick(String chars) {
        return chars.charAt(random.nextInt(chars.length()));
    }
}
