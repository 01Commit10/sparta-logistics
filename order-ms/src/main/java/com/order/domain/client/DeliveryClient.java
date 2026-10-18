package com.order.domain.client;

public interface DeliveryClient {
    /** 배송 생성.
     * 실패 (거절, 타임아웃, 서버 오류)는 모두 예외로 던진다 */
    DeliveryResult createDelivery(CreateDeliveryCommand command);

    //TODO: cancelDelivery
}