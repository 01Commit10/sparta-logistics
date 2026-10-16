package com.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 주문(order-ms) 오류 코드 (docs/design/04 API 명세서 ORDER).
 */
@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    // 400 - 입력 규칙 위반
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "수량은 1 이상 입력해주세요."),


    // 403 - 권한 없음

    // 404 - 주문없음, 참조하는 업체/상품 없음

    // 409 - 상태 충돌(이미 취소된 주문 등), 멱등성 키 중복

    ;

    private final HttpStatus httpStatus;
    private final String message;
}
