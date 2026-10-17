package com.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 상품(company-ms) 오류 코드 (docs/design/04 API 명세서 Product).
 */
@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    // 400
    PRODUCT_INVALID_UNIT_PRICE(HttpStatus.BAD_REQUEST, "상품 단가는 0 이상이어야 합니다."),
    ;

    private final HttpStatus httpStatus;
    private final String message;
}
