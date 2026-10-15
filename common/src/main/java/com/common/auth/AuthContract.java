package com.common.auth;

/**
 * 서비스 간 인증 계약 값 (docs/plan/02 인증 계약 2.1, 2.3). 상수만 두고 로직(발급·파싱·검증)은 각 서비스에 둔다.
 * <ul>
 *     <li>claim: user-ms가 발급하고 Gateway가 검증한다</li>
 *     <li>헤더: Gateway가 검증한 토큰에서 만들어 하위 서비스로 보낸다. 외부에서 온 같은 헤더는 Gateway가 지운다</li>
 * </ul>
 */
public final class AuthContract {

    /** 사용자 역할 (MASTER / USER). Access Token에만 있다 */
    public static final String CLAIM_ROLE = "role";
    /** 비밀번호 변경 필요 여부 (Boolean). Access Token에만 있다 */
    public static final String CLAIM_PASSWORD_CHANGE_REQUIRED = "pcr";
    /** 토큰 종류. Refresh Token을 Access Token으로 쓰지 못하게 구분한다 */
    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    /** 토큰 sub (사용자 UUID) */
    public static final String HEADER_USER_ID = "X-User-Id";
    /** 토큰 role */
    public static final String HEADER_USER_ROLE = "X-User-Role";
    /** 토큰 jti. user-ms 로그아웃에서 blacklist 키로 쓴다 */
    public static final String HEADER_TOKEN_JTI = "X-Token-Jti";
    /** 토큰 exp (epoch 초). user-ms 로그아웃에서 blacklist TTL 계산에 쓴다 */
    public static final String HEADER_TOKEN_EXP = "X-Token-Exp";

    private AuthContract() {
    }
}
