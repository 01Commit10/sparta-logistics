package com.user.application.dto;

import com.user.domain.entity.User;

import java.util.UUID;

/** 배정된 배송담당자. hubId는 담당자의 소속 허브(= 요청한 허브) */
public record DeliveryManagerResult(UUID id, String name, String slackId, UUID hubId) {

    public static DeliveryManagerResult from(User user) {
        return new DeliveryManagerResult(user.getId(), user.getName(), user.getSlackId(), user.getOrganizationId());
    }
}
