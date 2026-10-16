package com.common.auth.web;

import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUserHeaderParser.Absent;
import com.common.auth.web.CurrentUserHeaderParser.Invalid;
import com.common.auth.web.CurrentUserHeaderParser.Valid;
import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** {@code @CurrentUser AuthUser} 파라미터에 X-User-Id·X-User-Role로 만든 사용자를 넣는다 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && AuthUser.class.equals(parameter.getParameterType());
    }

    @Override
    public AuthUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                    NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        return switch (CurrentUserHeaderParser.parse(request)) {
            case Valid valid -> valid.user();
            case Invalid invalid -> throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
            case Absent absent -> {
                if (parameter.getParameterAnnotation(CurrentUser.class).required()) {
                    throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
                }
                yield null;
            }
        };
    }
}
