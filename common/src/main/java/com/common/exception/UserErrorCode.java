package com.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 사용자(user-ms) 오류 코드 (docs/design/04 API 명세서 Auth·User, docs/Common/SYS-01 6장).
 */
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    // 400 - 입력 규칙 위반
    INVALID_USERNAME(HttpStatus.BAD_REQUEST, "아이디는 4~10자의 영문 소문자와 숫자만 사용할 수 있습니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호는 8~15자이며 영문 대문자·소문자·숫자·특수문자를 모두 포함해야 합니다."),
    INVALID_ORGANIZATION(HttpStatus.BAD_REQUEST, "소속 정보가 올바르지 않습니다."),
    SAME_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호와 다른 비밀번호를 입력해 주세요."),

    // 401 - 로그인·비밀번호 확인 실패 (계정 존재 여부를 드러내지 않음)
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),

    // 404
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // 409 - 중복·상태 충돌
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    DUPLICATE_SLACK_ID(HttpStatus.CONFLICT, "이미 사용 중인 Slack ID입니다."),
    LAST_MASTER(HttpStatus.CONFLICT, "마지막 관리자는 탈퇴·강등·비활성화할 수 없습니다."),
    USER_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 사용자입니다."),
    USER_NOT_DELETED(HttpStatus.CONFLICT, "삭제되지 않은 사용자입니다."),
    ;

    private final HttpStatus httpStatus;
    private final String message;
}
