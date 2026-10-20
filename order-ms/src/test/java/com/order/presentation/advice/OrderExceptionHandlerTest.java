package com.order.presentation.advice;

import com.common.exception.ErrorResponse;
import com.common.exception.OrderErrorCode;
import com.order.domain.exception.DuplicateOrderRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class OrderExceptionHandlerTest {

    private final OrderExceptionHandler handler = new OrderExceptionHandler();

    @Test
    @DisplayName("같은 키의 요청이 처리 중이라 이력 저장이 막히면 409 PREVIOUS_REQUEST_NOT_SUCCEEDED로 응답한다")
    void duplicateOrderRequestIsConflict() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDuplicateOrderRequest(new DuplicateOrderRequestException("order-req-0001"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo(OrderErrorCode.PREVIOUS_REQUEST_NOT_SUCCEEDED.getCode());
    }
}
