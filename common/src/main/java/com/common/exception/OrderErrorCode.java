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
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "재고가 부족하여 주문할 수 없습니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "같은 멱등키로 다른 내용의 주문 요청이 들어왔습니다."),

    // 503 - 서버가 일시적으로 요청을 처리할 수 없는 상태
    INVENTORY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "재고 확인 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
    DELIVERY_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "배송 생성에 실패하여 주문이 처리되지 않았습니다. 잠시 후 다시 시도해 주세요."),


    ;

    private final HttpStatus httpStatus;
    private final String message;
}
