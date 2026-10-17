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

    //user-ms 호출 관련
    DELIVERY_REQUESTER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "요청한 사용자 정보를 찾을 수 없습니다.");


    private final HttpStatus httpStatus;
    private final String message;
}
