package com.order.domain.exception;

/**
 * 같은 멱등키의 다른 요청이 먼저 이력을 저장(선점)해서, 이 요청은 선점하지 못했다.
 * 선점 트랜잭션은 롤백되고, 호출한 쪽이 새 트랜잭션에서 앞 요청의 상태를 다시 보고 판단한다.
 */
public class DuplicateOrderRequestException extends RuntimeException {

    public DuplicateOrderRequestException(String idempotencyKey) {
        super("같은 멱등키의 요청이 이미 처리 중입니다. key=" + idempotencyKey);
    }
}
