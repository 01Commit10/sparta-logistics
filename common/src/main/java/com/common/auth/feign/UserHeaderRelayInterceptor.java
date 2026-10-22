package com.common.auth.feign;

import com.common.auth.AuthContract;
import com.common.auth.CurrentUserProvider;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * 사용자 요청에서 시작된 서비스 간 Feign 호출에 X-User-Id·X-User-Role·X-Request-Id를 이어 보낸다 (C01, SYS-01 5.2).
 * <ul>
 *     <li>사용자 값은 {@link CurrentUserProvider}가 검증한 값을 표준 형식으로 보낸다</li>
 *     <li>Feign 메서드에서 직접 지정한 헤더는 덮어쓰지 않는다. 사용자 헤더 두 개는 한 쌍으로 보고, 하나라도 지정했으면 둘 다 건드리지 않는다</li>
 *     <li>요청 밖(스케줄러 등)이거나 요청에 없는 헤더는 붙이지 않는다</li>
 *     <li>Authorization·X-Token-*은 보내지 않는다 (토큰은 Gateway에서 끝난다)</li>
 * </ul>
 * 상대가 우리 서비스인지 판별하지 않는다. Feign은 내부 서비스끼리만 쓰고 외부 API는 RestClient로 부른다 (C01 D2·D3).
 */
public class UserHeaderRelayInterceptor implements RequestInterceptor {

    private final CurrentUserProvider currentUserProvider;

    public UserHeaderRelayInterceptor(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public void apply(RequestTemplate template) {
        // RequestTemplate 헤더 이름은 대소문자를 구분하지 않는다
        boolean userHeadersSet = template.headers().containsKey(AuthContract.HEADER_USER_ID)
                || template.headers().containsKey(AuthContract.HEADER_USER_ROLE);
        if (!userHeadersSet) {
            currentUserProvider.current().ifPresent(user -> {
                template.header(AuthContract.HEADER_USER_ID, user.id().toString());
                template.header(AuthContract.HEADER_USER_ROLE, user.role().name());
            });
        }
        if (!template.headers().containsKey(AuthContract.HEADER_REQUEST_ID)) {
            currentRequestId().ifPresent(id -> template.header(AuthContract.HEADER_REQUEST_ID, id));
        }
    }

    private static Optional<String> currentRequestId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }
        String requestId = attributes.getRequest().getHeader(AuthContract.HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(requestId.strip());
    }
}
