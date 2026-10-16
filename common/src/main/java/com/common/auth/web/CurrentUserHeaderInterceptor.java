package com.common.auth.web;

import com.common.auth.web.CurrentUserHeaderParser.Invalid;
import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 요청 입구 검사 (C01 D5). 모든 MVC 요청에 적용한다.
 * <ul>
 *     <li>X-User-* 없음 → 통과 (공개 API, 스케줄러에서 시작된 내부 호출)</li>
 *     <li>둘 다 있고 형식이 맞음 → 통과</li>
 *     <li>하나만 있음, 형식이 틀림 → 401 UNAUTHENTICATED (Gateway를 거치지 않은 위조 요청이나 버그로 봄)</li>
 * </ul>
 * 필터가 아니라 인터셉터라 GlobalExceptionHandler가 공통 오류 형식으로 응답한다.
 * 이 검사를 지난 뒤에는 Provider·AuditorAware·Feign이 "없음 또는 올바름"만 본다.
 */
@Slf4j
public class CurrentUserHeaderInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CurrentUserHeaderParser.parse(request) instanceof Invalid) {
            // 헤더 값은 위조 값일 수 있어 남기지 않는다
            log.warn("[InvalidUserHeader] {} {}", request.getMethod(), request.getRequestURI());
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return true;
    }
}
