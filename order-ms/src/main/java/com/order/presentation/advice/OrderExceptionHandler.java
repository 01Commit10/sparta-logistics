package com.order.presentation.advice;

import com.common.exception.ErrorResponse;
import com.common.exception.OrderErrorCode;
import com.order.domain.exception.DuplicateOrderRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * order-ms 전용 예외 응답. 공통 GlobalExceptionHandler보다 먼저 처리한다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class OrderExceptionHandler {

    // 같은 키의 앞 요청이 아직 처리 중 → 앞 요청이 끝난 뒤 같은 키로 다시 보내면 그 결과를 받는다
    @ExceptionHandler(DuplicateOrderRequestException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateOrderRequest(DuplicateOrderRequestException e) {
        OrderErrorCode errorCode = OrderErrorCode.PREVIOUS_REQUEST_NOT_SUCCEEDED;
        log.warn("[DuplicateOrderRequest] {}", e.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of()));
    }
}
