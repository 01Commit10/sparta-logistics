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
    DELIVERY_NOT_CANCELABLE(HttpStatus.CONFLICT, "이동이 시작된 배송은 취소할 수 없습니다."),
    DELIVERY_ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "배송 구간을 찾을 수 없습니다."),
    DELIVERY_ROUTE_TYPE_NOT_FOUND(HttpStatus.BAD_REQUEST, "해당 배송에 없는 구간 유형입니다."),
    DELIVERY_ROUTE_NOT_ASSIGNABLE(HttpStatus.CONFLICT, "완료되거나 취소된 구간에는 담당자를 배정할 수 없습니다."),
    DELIVERY_INVALID_REQUESTER_COMPANY(HttpStatus.BAD_REQUEST, "요청 업체를 찾을 수 없습니다."),
    DELIVERY_INVALID_RECIPIENT(HttpStatus.BAD_REQUEST, "수령인 정보를 찾을 수 없습니다."),

    // user-ms 호출 관련
    DELIVERY_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 사용자 정보를 찾을 수 없습니다."),

    // hub-ms 호출 관련
    HUB_ROUTE_NOT_FOUND(HttpStatus.BAD_REQUEST, "출발 허브와 도착 허브 사이의 경로를 찾을 수 없습니다.");


    private final HttpStatus httpStatus;
    private final String message;
}
