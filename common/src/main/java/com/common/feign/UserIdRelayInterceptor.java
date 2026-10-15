package com.common.feign;

import com.common.web.UserHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * 사용자 요청에서 시작된 서비스 간 호출에 X-User-Id를 그대로 전달 (docs/Common/SYS-01 5.2).
 * 호출받은 서비스가 감사 컬럼(created_by 등)을 기록할 수 있게 하기 위함.
 * - HTTP 요청 밖(스케줄러, 비동기 스레드)에서의 호출엔 헤더를 붙이지 않음.
 * - Feign 메서드에서 헤더를 직접 지정했다면 덮어쓰지 않음.
 */
public class UserIdRelayInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (template.headers().containsKey(UserHeaders.USER_ID)) {
            return;
        }
        UserHeaders.currentUserId().ifPresent(userId -> template.header(UserHeaders.USER_ID, userId));
    }
}
