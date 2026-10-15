package com.user.domain.user;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;

import java.util.regex.Pattern;

/**
 * 비밀번호 규칙: 8~15자, 영문 대문자·소문자·숫자·특수문자를 모두 포함 (발제 361행).
 * 원문 검사만 한다. 해시는 PasswordHasher가 만든다.
 */
public final class PasswordPolicy {

    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9\\p{Punct}]{8,15}$");
    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("\\p{Punct}");

    private PasswordPolicy() {
    }

    public static void validate(String raw) {
        if (raw == null
                || !ALLOWED.matcher(raw).matches()
                || !UPPER.matcher(raw).find()
                || !LOWER.matcher(raw).find()
                || !DIGIT.matcher(raw).find()
                || !SPECIAL.matcher(raw).find()) {
            throw new BusinessException(UserErrorCode.INVALID_PASSWORD);
        }
    }
}
