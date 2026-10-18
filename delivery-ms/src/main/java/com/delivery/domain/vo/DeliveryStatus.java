package com.delivery.domain.vo;

public enum DeliveryStatus {
    HUB_WAITING, // 출발 허브에서 출고 대기 (아직 아무 이동도 시작 안 함)
    HUB_MOVING, // 허브 간 이동 중
    HUB_ARRIVED, // 경우 1) 허브 간 이동을 마치고 목적지 허브에 도착, 경우 2) 출발 허브와 도착 허브가 같을 경우 출고 대기(아무 이동도 시작 안 함)
    DELIVERING, // 목적지 허브에서 업체로 배송 중
    DELIVERED, // 수령 완료
    CANCELED // 취소
}
