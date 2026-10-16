package com.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DeliveryErrorCode implements ErrorCode{

    DELIVERY_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 해당 주문의 배송이 존재합니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
