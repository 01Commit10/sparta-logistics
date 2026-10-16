package com.gateway.presentation.filter;

/**
 * 필터끼리 주고받는 exchange attribute 이름. attribute는 exchange를 mutate해도 같은 Map을 공유한다.
 */
public final class GatewayAttributes {

    /** 인증된 사용자 UUID 문자열. AuthenticationFilter가 넣고 RequestIdFilter가 접근 로그에 쓴다 */
    public static final String USER_ID = "gateway.userId";

    private GatewayAttributes() {
    }
}
