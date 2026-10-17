package com.user.domain.service;

/**
 * 비밀번호 초기화용 임시 비밀번호 생성 Port (A12). 결과는 항상 PasswordPolicy를 통과해야 한다.
 * 구현은 infrastructure.security (SecureRandom).
 */
public interface TemporaryPasswordGenerator {

    String generate();
}
