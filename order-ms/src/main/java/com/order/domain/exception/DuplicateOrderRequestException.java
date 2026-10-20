package com.order.domain.exception;

/**
 * 같은 멱등키의 다른 요청이 먼저 이력을 저장해 이 요청은 처리하지 않는다.
 * BusinessException이 아니므로 주문 트랜잭션은 롤백되고(noRollbackFor 대상 아님), 응답은 presentation에서 409로 바꾼다.
 */
public class DuplicateOrderRequestException extends RuntimeException {

    public DuplicateOrderRequestException(String idempotencyKey) {
        super("같은 멱등키의 요청이 이미 처리 중입니다. key=" + idempotencyKey);
    }
}
