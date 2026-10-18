package com.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DeliveryErrorCode implements ErrorCode{

    DELIVERY_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 해당 주문의 배송이 존재합니다."),
    DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "배송을 찾을 수 없습니다."),
    DELIVERY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 배송에 접근할 권한이 없습니다."),
    INVALID_DELIVERY_MANAGER(HttpStatus.BAD_REQUEST, "배송담당자가 아닌 사용자는 배정할 수 없습니다."),
    DELIVERY_INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "현재 배송 상태에서 변경할 수 없는 상태입니다."),

    // DB 저장 충돌
    DUPLICATE_IDEMPOTENT_REQUEST(HttpStatus.CONFLICT, "같은 작업에 같은 멱등성 키로 처리된 요청이 이미 있습니다."),

    //user-ms 호출 관련
    DELIVERY_REQUESTER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "요청한 사용자 정보를 찾을 수 없습니다.");


    private final HttpStatus httpStatus;
    private final String message;
}
