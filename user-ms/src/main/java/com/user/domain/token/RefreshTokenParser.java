package com.user.domain.token;

/**
 * Refresh Token 검증 Port. 구현은 infrastructure.jwt (jjwt, HS256).
 * 서명 오류·만료·형식 오류·Access Token(typ=access)은 모두 REFRESH_TOKEN_INVALID (사유를 드러내지 않음).
 */
public interface RefreshTokenParser {

    RefreshTokenClaims parse(String token);
}
