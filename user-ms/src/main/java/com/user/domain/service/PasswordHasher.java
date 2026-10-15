package com.user.domain.service;

/**
 * 비밀번호 해시 Port. 구현은 infrastructure.security (BCrypt).
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
