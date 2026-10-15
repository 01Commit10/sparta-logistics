package com.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 인증·인가 오류 코드 (docs/Common/SYS-01 게이트웨이 인증 인가 설계 6장).
 * Gateway가 토큰 검증 단계에서 반환한다. user-ms의 로그인·재발급 오류 코드도 이 enum에 추가한다.
 * 권한 부족(ACCESS_DENIED)·인증 헤더 없음(UNAUTHENTICATED)은 CommonErrorCode를 사용한다.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    // 401 - 클라이언트가 다시 로그인하거나 토큰을 재발급해야 함
    TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "인증 토큰이 없습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증 토큰이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 토큰입니다."),
    TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "더 이상 사용할 수 없는 인증 토큰입니다. 다시 로그인해 주세요."),

    // 403 - 관리자가 초기화한 비밀번호를 아직 변경하지 않음
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "비밀번호를 변경한 뒤 이용할 수 있습니다."),

    // 503 - 토큰 상태 저장소(Redis) 장애. 로그아웃한 토큰이 통과되지 않도록 요청을 거부함
    AUTH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 인증을 처리할 수 없습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String message;
}
