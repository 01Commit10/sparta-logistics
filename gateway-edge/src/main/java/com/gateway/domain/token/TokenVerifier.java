package com.gateway.domain.token;

/**
 * Access Token 검증 Port. 구현은 infrastructure(jjwt)에 둔다.
 * 서명 검증은 CPU 연산이라 Mono 없이 동기로 둔다.
 */
public interface TokenVerifier {

    VerificationResult verify(String token);
}
