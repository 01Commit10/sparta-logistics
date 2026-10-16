package com.user.domain.vo;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;

import java.util.regex.Pattern;

/**
 * 로그인 ID. 4~10자, 영문 소문자(a~z)·숫자(0~9) (발제 360행).
 */
public record Username(String value) {

    private static final Pattern RULE = Pattern.compile("^[a-z0-9]{4,10}$");

    public Username {
        if (value == null || !RULE.matcher(value).matches()) {
            throw new BusinessException(UserErrorCode.INVALID_USERNAME);
        }
    }

    public static Username of(String value) {
        return new Username(value);
    }
}
