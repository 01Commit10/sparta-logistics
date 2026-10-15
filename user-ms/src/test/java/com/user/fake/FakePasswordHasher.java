package com.user.fake;

import com.user.domain.service.PasswordHasher;

/** 테스트용: 해시 대신 접두사만 붙인다. */
public class FakePasswordHasher implements PasswordHasher {

    @Override
    public String hash(String rawPassword) {
        return "hashed:" + rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return passwordHash.equals(hash(rawPassword));
    }
}
