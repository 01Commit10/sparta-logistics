package com.order.domain.client;

import java.util.UUID;

public interface DeliveryClient {
    /** 배송 생성.
     * 실패 (거절, 타임아웃, 서버 오류)는 모두 예외로 던진다 */
    DeliveryResult createDelivery(CreateDeliveryCommand command);

    /** 출발 전이면 취소, 이미 취소됐으면 아무것도 안 함, 출발했으면 409 예외 */
    void cancelDelivery(UUID deliveryId);

}