package com.common.auth;

import java.util.Optional;

/**
 * Controller 밖(AuditorAware, Feign 인터셉터 등)에서 현재 사용자를 꺼낸다. 예외를 던지지 않는다.
 * Controller에서는 {@code @CurrentUser AuthUser}를 쓴다.
 */
public interface CurrentUserProvider {

    /** 요청 안이고 사용자 헤더가 올바르면 그 사용자. 요청 밖이거나 헤더가 없으면 empty */
    Optional<AuthUser> current();

    /** HTTP 요청을 처리하는 중인지. false면 스케줄러·기동 작업처럼 요청 밖이다 (감사 작업자 = 시스템, C01 D7) */
    boolean inRequest();
}
