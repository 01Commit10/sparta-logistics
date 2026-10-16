package com.common.auth.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller 파라미터에 현재 사용자({@link com.common.auth.AuthUser})를 넣는다.
 * <pre>
 * public UserResponse me(@CurrentUser AuthUser user)                      // 헤더 없으면 401
 * public SignupResponse signup(@CurrentUser(required = false) AuthUser user, …)  // 헤더 없으면 null
 * </pre>
 * 형식이 틀린 헤더는 required와 상관없이 401이다. 내부 API(/internal/**)에는 필수로 쓰지 않는다 (C01 D8).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {

    /** false면 헤더가 없을 때 null을 넣는다 */
    boolean required() default true;
}
