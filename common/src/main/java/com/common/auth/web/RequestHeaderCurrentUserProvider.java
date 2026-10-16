package com.common.auth.web;

import com.common.auth.AuthUser;
import com.common.auth.CurrentUserProvider;
import com.common.auth.web.CurrentUserHeaderParser.Valid;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * 현재 스레드의 서블릿 요청 헤더에서 사용자를 꺼낸다.
 * 잘못된 헤더는 입구 인터셉터가 이미 401로 끝냈으므로 여기서는 empty로만 본다.
 */
public class RequestHeaderCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Optional<AuthUser> current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }
        return CurrentUserHeaderParser.parse(attributes.getRequest()) instanceof Valid valid
                ? Optional.of(valid.user())
                : Optional.empty();
    }

    @Override
    public boolean inRequest() {
        return RequestContextHolder.getRequestAttributes() != null;
    }
}
