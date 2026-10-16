package com.common.auth;

import java.util.UUID;

/**
 * 서비스 간 인증 계약 값 (docs/plan/02 인증 계약 2.1~2.3). 상수만 두고 로직(발급·파싱·검증)은 각 서비스에 둔다.
 * <ul>
 *     <li>claim: user-ms가 발급하고 Gateway가 검증한다</li>
 *     <li>헤더: Gateway가 검증한 토큰에서 만들어 하위 서비스로 보낸다. 외부에서 온 같은 헤더는 Gateway가 지운다</li>
 *     <li>Redis 키: user-ms가 쓰고 Gateway가 읽는다. 접두사 뒤에 사용자 UUID 또는 jti를 붙인다</li>
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
    /** 요청 추적 ID. Gateway가 넣고(GW-08) 서비스 간 Feign 호출에 그대로 이어 보낸다 */
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    /** 값: Refresh Token jti, TTL: Refresh 유효 시간. 키: 접두사 + 사용자 UUID (사용자당 1개) */
    public static final String REDIS_REFRESH_PREFIX = "auth:refresh:";
    /** 값: "logout", TTL: Access 남은 유효 시간. 키: 접두사 + Access jti (U08 로그아웃) */
    public static final String REDIS_BLACKLIST_PREFIX = "auth:blacklist:";
    /** 값: epoch 초, TTL: Access 유효 시간. 키: 접두사 + 사용자 UUID. Gateway는 iat <= 값인 토큰을 거부한다 (U10 등) */
    public static final String REDIS_REVOKED_AT_PREFIX = "auth:revoked-at:";

    /**
     * 요청 밖 작업(스케줄러, 기동 시 초기화, 시드)의 감사 작업자 (07 정책 1.4).
     * 실제 사용자는 무작위 UUID v4라 이 값과 겹치지 않는다
     */
    public static final UUID SYSTEM_USER_ID = new UUID(0L, 0L);

    private AuthContract() {
    }
}
