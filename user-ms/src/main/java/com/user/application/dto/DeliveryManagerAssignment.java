package com.user.application.dto;

/** 배송 생성 시 무작위 배정 결과: 출발지 허브 담당자와 도착지 허브 담당자 (같은 사람일 수 있다) */
public record DeliveryManagerAssignment(DeliveryManagerResult departureManager, DeliveryManagerResult arrivalManager) {
}
