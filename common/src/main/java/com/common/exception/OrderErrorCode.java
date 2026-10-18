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
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "주문 수량은 1 이상 입력해주세요."),
    INVALID_UNIT_PRICE(HttpStatus.BAD_REQUEST, "주문 단가가 0원 이상인지 확인해주세요."),


    // 403 - 권한 없음

    // 404 - 주문없음, 참조하는 업체/상품 없음

    // 409 - 상태 충돌 (이미 취소된 주문 등), 멱등성 키 중복
    HISTORY_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 완료된 요청입니다."),
    ORDER_NOT_PROCESSING(HttpStatus.CONFLICT, "처리 중인 주문만 완료 또는 실패 처리할 수 있습니다."),


    ;

    private final HttpStatus httpStatus;
    private final String message;
}
