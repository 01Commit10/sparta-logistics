package com.user.presentation.dto.response;

import com.user.application.dto.DeliveryManagerAssignment;

import java.util.UUID;

/**
 * 서비스 간 계약 응답 (USR-23, 소비자: delivery-ms). delivery-ms가 요청한 대로 담당자 id 두 개만 평평하게 내보낸다
 * (이름·Slack ID 미노출). 필드를 바꾸면 delivery-ms의 Feign DTO도 함께 바꿔야 한다. 필드 추가는 뒤에 붙인다.
 */
public record DeliveryManagerAssignmentResponse(UUID departureManagerId, UUID arrivalManagerId) {

    public static DeliveryManagerAssignmentResponse from(DeliveryManagerAssignment assignment) {
        return new DeliveryManagerAssignmentResponse(assignment.departureManager().id(),
                assignment.arrivalManager().id());
    }
}
