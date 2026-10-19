package com.user.presentation.dto.response;

import com.user.application.dto.DeliveryManagerAssignment;
import com.user.application.dto.DeliveryManagerResult;

import java.util.UUID;

/**
 * 서비스 간 계약 응답 (USR-23, 소비자: delivery-ms). 꼭 쓰는 값은 각 담당자의 id다.
 * 필드를 바꾸면 delivery-ms의 Feign DTO도 함께 바꿔야 한다. 필드 추가는 뒤에 붙인다 (받는 쪽은 모르는 필드를 무시).
 */
public record DeliveryManagerAssignmentResponse(Manager departureManager, Manager arrivalManager) {

    public record Manager(UUID id, String name, String slackId, UUID hubId) {

        static Manager from(DeliveryManagerResult result) {
            return new Manager(result.id(), result.name(), result.slackId(), result.hubId());
        }
    }

    public static DeliveryManagerAssignmentResponse from(DeliveryManagerAssignment assignment) {
        return new DeliveryManagerAssignmentResponse(Manager.from(assignment.departureManager()),
                Manager.from(assignment.arrivalManager()));
    }
}
